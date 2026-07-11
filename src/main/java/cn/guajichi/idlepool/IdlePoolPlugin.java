package cn.guajichi.idlepool;

import cn.guajichi.idlepool.command.AfkPoolCommand;
import cn.guajichi.idlepool.admin.PoolAdminGuiManager;
import cn.guajichi.idlepool.admin.RewardAdminGuiManager;
import cn.guajichi.idlepool.gui.PoolGuiManager;
import cn.guajichi.idlepool.gui.InboxGuiManager;
import cn.guajichi.idlepool.integration.EconomyBridge;
import cn.guajichi.idlepool.integration.ItemsAdderVisualBridge;
import cn.guajichi.idlepool.integration.VanillaVisualBridge;
import cn.guajichi.idlepool.integration.VisualBridge;
import cn.guajichi.idlepool.listener.PoolPresenceListener;
import cn.guajichi.idlepool.diagnostic.DoctorService;
import cn.guajichi.idlepool.diagnostic.PluginInfoService;
import cn.guajichi.idlepool.pool.PoolRepository;
import cn.guajichi.idlepool.reward.RewardPlanRepository;
import cn.guajichi.idlepool.reward.RewardService;
import cn.guajichi.idlepool.reward.ItemRewardFactory;
import cn.guajichi.idlepool.reward.HeldItemIdentifier;
import cn.guajichi.idlepool.reward.SnapshotRepository;
import cn.guajichi.idlepool.session.SessionManager;
import cn.guajichi.idlepool.storage.SqliteStore;
import cn.guajichi.idlepool.util.DurationParser;
import cn.guajichi.idlepool.update.UpdateChecker;
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
        RewardService rewards = new RewardService(this, rewardPlans, store, economy);
        sessions = new SessionManager(this, store, rewards, visuals);
        PoolGuiManager gui = new PoolGuiManager(this, visuals, sessions);
        ItemRewardFactory itemFactory = new ItemRewardFactory(visuals, getLogger(), snapshots);
        InboxGuiManager inboxGui = new InboxGuiManager(this, store, itemFactory);
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
            return new VanillaVisualBridge("未安装");
        }
        try {
            return new ItemsAdderVisualBridge(this);
        } catch (RuntimeException | LinkageError error) {
            getLogger().warning("ItemsAdder API 版本不兼容，将使用原版按钮和 ActionBar 文本：" + error.getMessage());
            String detail = error.getMessage() == null || error.getMessage().isBlank()
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            return new VanillaVisualBridge("错误：" + detail);
        }
    }

    private void saveBundledFile(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.exists()) {
            saveResource(name, false);
        }
    }
}
