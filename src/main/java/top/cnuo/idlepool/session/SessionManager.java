package top.cnuo.idlepool.session;

import top.cnuo.idlepool.integration.VisualBridge;
import top.cnuo.idlepool.gui.InboxGuiManager;
import top.cnuo.idlepool.pool.PoolDefinition;
import top.cnuo.idlepool.reward.RewardService;
import top.cnuo.idlepool.storage.ProgressRecord;
import top.cnuo.idlepool.storage.SqliteStore;
import top.cnuo.idlepool.util.Messages;
import top.cnuo.idlepool.util.TimeFormats;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SessionManager implements AutoCloseable {
    private final JavaPlugin plugin;
    private final SqliteStore store;
    private final RewardService rewards;
    private final VisualBridge visuals;
    private final InboxGuiManager inbox;
    private final Map<UUID, ActiveSession> active = new HashMap<>();
    private final Set<UUID> starting = new HashSet<>();
    private BukkitTask ticker;
    private int checkpointCounter;

    public SessionManager(
            JavaPlugin plugin,
            SqliteStore store,
            RewardService rewards,
            VisualBridge visuals,
            InboxGuiManager inbox
    ) {
        this.plugin = plugin;
        this.store = store;
        this.rewards = rewards;
        this.visuals = visuals;
        this.inbox = inbox;
    }

    public void beginTicking() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void start(Player player, PoolDefinition pool) {
        UUID playerId = player.getUniqueId();
        if (active.containsKey(playerId)) {
            send(player, "already-active", Map.of());
            return;
        }
        if (!pool.permission().isBlank() && !player.hasPermission(pool.permission())) {
            send(player, "no-permission", Map.of());
            return;
        }
        if (pool.maxActivePlayers() > 0 && activeCount(pool.id()) >= pool.maxActivePlayers()) {
            send(player, "pool-full", Map.of());
            return;
        }
        if (!starting.add(playerId)) {
            return;
        }

        player.closeInventory();
        send(player, "progress-loading", Map.of());
        store.loadProgress(playerId, pool.id()).whenComplete((loaded, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> finishStart(playerId, pool, loaded, failure))
        );
    }

    private void finishStart(UUID playerId, PoolDefinition pool, ProgressRecord loaded, Throwable failure) {
        starting.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || failure != null || active.containsKey(playerId)) {
            return;
        }
        if (!pool.enabled() || !pool.region().contains(player.getLocation())) {
            send(player, "no-pool", Map.of());
            return;
        }
        if (pool.maxActivePlayers() > 0 && activeCount(pool.id()) >= pool.maxActivePlayers()) {
            send(player, "pool-full", Map.of());
            return;
        }

        ProgressRecord progress = loaded.discardExpired(Instant.now().getEpochSecond());
        ActiveSession session = new ActiveSession(playerId, pool, progress);
        active.put(playerId, session);
        send(player, "started", Map.of("pool", plainPoolName(pool)));
        updateActionBar(player, session);
    }

    public void stop(Player player, boolean leftRegion) {
        stop(player, leftRegion, true);
    }

    public void stop(Player player, boolean leftRegion, boolean openInbox) {
        ActiveSession session = active.remove(player.getUniqueId());
        starting.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        save(session);
        send(player, leftRegion ? "left-region" : "stopped", Map.of(
                "session_time", TimeFormats.clock(session.sessionSeconds())
        ));
        player.sendActionBar(net.kyori.adventure.text.Component.empty());
        if (openInbox && player.isOnline() && plugin.getConfig().getBoolean("inbox.open-on-stop", true)) {
            inbox.open(player);
        }
    }

    public boolean isActive(UUID playerId) {
        return active.containsKey(playerId);
    }

    public Optional<ActiveSession> find(UUID playerId) {
        return Optional.ofNullable(active.get(playerId));
    }

    public int activeCount(String poolId) {
        return (int) active.values().stream().filter(session -> session.pool().id().equals(poolId)).count();
    }

    private void tick() {
        checkpointCounter++;
        int checkpointInterval = Math.max(5, plugin.getConfig().getInt("storage.checkpoint-interval-seconds", 30));

        for (ActiveSession session : new java.util.ArrayList<>(active.values())) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                active.remove(session.playerId());
                save(session);
                continue;
            }
            if (!session.pool().region().contains(player.getLocation())) {
                stop(player, true);
                continue;
            }

            long previousSessionSeconds = session.sessionSeconds();
            session.tick();
            RewardService.MilestoneGrant milestone = rewards.grantMilestones(
                    player, session.pool(), previousSessionSeconds, session.sessionSeconds()
            );
            if (milestone.eligible() > 0) {
                session.recordMilestone(milestone.granted());
                send(player, "milestone-reached", Map.of(
                        "time", TimeFormats.clock(session.sessionSeconds()),
                        "earned", Integer.toString(milestone.granted())
                ));
            }
            while (session.cycleReady()) {
                int granted = rewards.grantCycle(player, session.pool(), session.sessionSeconds());
                session.completeCycle(granted);
            }
            updateActionBar(player, session);

            if (checkpointCounter >= checkpointInterval) {
                save(session);
            }
        }
        if (checkpointCounter >= checkpointInterval) {
            checkpointCounter = 0;
        }
    }

    private void updateActionBar(Player player, ActiveSession session) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("actionbar.enabled", true)) {
            return;
        }
        long next = Math.max(0, session.pool().rewardCycle().toSeconds() - session.progressSeconds());
        String format = Messages.raw("actionbar.format");
        format = replaceVisualToken(format, "clock");
        format = replaceVisualToken(format, "gift");
        format = replaceVisualToken(format, "coin");
        player.sendActionBar(Messages.parse(format, Map.of(
                "pool", plainPoolName(session.pool()),
                "session_time", TimeFormats.clock(session.sessionSeconds()),
                "total_time", TimeFormats.clock(session.totalSeconds()),
                "next_reward", TimeFormats.clock(next),
                "earned", Long.toString(session.earned()),
                "players", Integer.toString(activeCount(session.pool().id()))
        )));
    }

    private String replaceVisualToken(String input, String token) {
        String id = plugin.getConfig().getString("itemsadder.font-images." + token, "");
        String glyph = visuals.fontImage(id);
        return input.replace(":" + token + ":", glyph);
    }

    private void save(ActiveSession session) {
        long expiresAt = Instant.now().plus(session.pool().progressRetention()).getEpochSecond();
        store.saveProgress(session.playerId(), session.pool().id(), session.snapshot(expiresAt));
    }

    private void send(Player player, String key, Map<String, String> placeholders) {
        Messages.send(player, "session." + key, placeholders);
    }

    private static String plainPoolName(PoolDefinition pool) {
        return pool.id();
    }

    @Override
    public void close() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (ActiveSession session : active.values()) {
            save(session);
        }
        active.clear();
        starting.clear();
    }
}
