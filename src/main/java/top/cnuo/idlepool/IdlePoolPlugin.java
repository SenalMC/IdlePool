package top.cnuo.idlepool;

import top.cnuo.idlepool.command.AfkPoolCommand;
import top.cnuo.idlepool.admin.PoolAdminGuiManager;
import top.cnuo.idlepool.admin.RewardAdminGuiManager;
import top.cnuo.idlepool.gui.PoolGuiManager;
import top.cnuo.idlepool.gui.InboxGuiManager;
import top.cnuo.idlepool.integration.EconomyBridge;
import top.cnuo.idlepool.integration.ItemsAdderVisualBridge;
import top.cnuo.idlepool.integration.VanillaVisualBridge;
import top.cnuo.idlepool.integration.VisualBridge;
import top.cnuo.idlepool.listener.PoolPresenceListener;
import top.cnuo.idlepool.diagnostic.DoctorService;
import top.cnuo.idlepool.diagnostic.PluginInfoService;
import top.cnuo.idlepool.pool.PoolRepository;
import top.cnuo.idlepool.reward.RewardPlanRepository;
import top.cnuo.idlepool.reward.RewardService;
import top.cnuo.idlepool.reward.ItemRewardFactory;
import top.cnuo.idlepool.reward.HeldItemIdentifier;
import top.cnuo.idlepool.reward.SnapshotRepository;
import top.cnuo.idlepool.session.SessionManager;
import top.cnuo.idlepool.storage.SqliteStore;
import top.cnuo.idlepool.util.DurationParser;
import top.cnuo.idlepool.util.Messages;
import top.cnuo.idlepool.update.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;

public final class IdlePoolPlugin extends JavaPlugin {
    private PoolRepository pools;
    private RewardPlanRepository rewardPlans;
    private SqliteStore store;
    private SessionManager sessions;
    private PoolAdminGuiManager adminGui;
    private RewardAdminGuiManager rewardAdminGui;
    private SnapshotRepository snapshots;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledFile("pools.yml");
        saveBundledFile("rewards.yml");
        saveBundledFile("message.yml");
        saveBundledFile("en.yml");

        store = new SqliteStore(this, new File(getDataFolder(), getConfig().getString("storage.file", "data.db")));
        try {
            store.initialize();
        } catch (RuntimeException exception) {
            getLogger().severe("SQLite 初始化失败，插件将被禁用：" + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pools = new PoolRepository(this);
        rewardPlans = new RewardPlanRepository(this);
        snapshots = new SnapshotRepository(this);
        reloadIdlePool();

        VisualBridge visuals = createVisualBridge();
        UpdateChecker updateChecker = new UpdateChecker(this);
        PluginInfoService info = new PluginInfoService(this, visuals, updateChecker);
        EconomyBridge economy = new EconomyBridge(getLogger());
        DoctorService doctor = new DoctorService(pools, rewardPlans, visuals, economy);
        ItemRewardFactory itemFactory = new ItemRewardFactory(visuals, getLogger(), snapshots);
        InboxGuiManager inboxGui = new InboxGuiManager(this, store, itemFactory);
        RewardService rewards = new RewardService(this, rewardPlans, store, economy);
        sessions = new SessionManager(this, store, rewards, visuals, inboxGui);
        PoolGuiManager gui = new PoolGuiManager(this, visuals, sessions);
        adminGui = new PoolAdminGuiManager(this, pools);
        rewardAdminGui = new RewardAdminGuiManager(
                this, rewardPlans, itemFactory, new HeldItemIdentifier(visuals, getLogger())
        );
        PoolPresenceListener presence = new PoolPresenceListener(this, pools, gui, sessions);

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(inboxGui, this);
        getServer().getPluginManager().registerEvents(adminGui, this);
        getServer().getPluginManager().registerEvents(rewardAdminGui, this);
        getServer().getPluginManager().registerEvents(presence, this);

        AfkPoolCommand command = new AfkPoolCommand(
                this, pools, gui, sessions, adminGui, rewardAdminGui, inboxGui, doctor, info
        );
        PluginCommand pluginCommand = getCommand("afkpool");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command afkpool is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        sessions.beginTicking();
        updateChecker.checkAsync();
        getLogger().info("IdlePool 已启用。ItemsAdder=" + visuals.available() + ", Vault=" + economy.available());
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.close();
        }
        if (adminGui != null) {
            adminGui.close();
        }
        if (rewardAdminGui != null) {
            rewardAdminGui.close();
        }
        if (store != null) {
            store.close();
        }
    }

    public void reloadIdlePool() {
        reloadConfig();
        Messages.load(this);
        Duration defaultRetention = DurationParser.parse(getConfig().getString("progress.default-retention", "7d"));
        if (pools != null) {
            pools.reload(defaultRetention);
        }
        if (rewardPlans != null) {
            rewardPlans.reload();
        }
        if (snapshots != null) {
            snapshots.reload();
        }
    }

    private VisualBridge createVisualBridge() {
        if (!getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            getLogger().warning("未检测到 ItemsAdder，将使用原版按钮和 ActionBar 文本；核心挂机功能仍可使用。");
            return new VanillaVisualBridge(Messages.raw("itemsadder.status.not-installed"));
        }
        try {
            return new ItemsAdderVisualBridge(this);
        } catch (RuntimeException | LinkageError error) {
            getLogger().warning("ItemsAdder API 版本不兼容，将使用原版按钮和 ActionBar 文本：" + error.getMessage());
            String detail = error.getMessage() == null || error.getMessage().isBlank()
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            return new VanillaVisualBridge(Messages.raw("itemsadder.status.error", java.util.Map.of("error", detail)));
        }
    }

    private void saveBundledFile(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.exists()) {
            saveResource(name, false);
        }
    }
}
