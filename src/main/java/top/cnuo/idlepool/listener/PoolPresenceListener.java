package top.cnuo.idlepool.listener;

import top.cnuo.idlepool.gui.PoolGuiManager;
import top.cnuo.idlepool.pool.PoolDefinition;
import top.cnuo.idlepool.pool.PoolRepository;
import top.cnuo.idlepool.session.ActiveSession;
import top.cnuo.idlepool.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PoolPresenceListener implements Listener {
    private final JavaPlugin plugin;
    private final PoolRepository pools;
    private final PoolGuiManager gui;
    private final SessionManager sessions;
    private final Map<UUID, String> observedPool = new HashMap<>();

    public PoolPresenceListener(JavaPlugin plugin, PoolRepository pools, PoolGuiManager gui, SessionManager sessions) {
        this.plugin = plugin;
        this.pools = pools;
        this.gui = gui;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!changedBlock(event.getFrom(), event.getTo())) {
            return;
        }
        handlePosition(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        schedulePositionCheck(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        observedPool.remove(event.getPlayer().getUniqueId());
        schedulePositionCheck(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        observedPool.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> handlePosition(event.getPlayer(), event.getPlayer().getLocation()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        observedPool.remove(player.getUniqueId());
        sessions.stop(player, false, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        observedPool.remove(event.getPlayer().getUniqueId());
        sessions.stop(event.getPlayer(), false, false);
    }

    private void handlePosition(Player player, Location location) {
        Optional<ActiveSession> active = sessions.find(player.getUniqueId());
        if (active.isPresent()) {
            if (!active.get().pool().region().contains(location)) {
                sessions.stop(player, true);
            }
            observedPool.put(player.getUniqueId(), pools.at(location).map(PoolDefinition::id).orElse(""));
            return;
        }

        Optional<PoolDefinition> current = pools.at(location);
        String currentId = current.map(PoolDefinition::id).orElse("");
        String previousId = observedPool.put(player.getUniqueId(), currentId);
        if (!currentId.isBlank() && !currentId.equals(previousId)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!sessions.isActive(player.getUniqueId()) && current.get().region().contains(player.getLocation())) {
                    gui.open(player, current.get());
                }
            });
        }
    }

    private void schedulePositionCheck(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> handlePosition(player, player.getLocation()));
    }

    private static boolean changedBlock(Location from, Location to) {
        return from.getWorld() != to.getWorld()
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
