package top.cnuo.idlepool.listener

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.gui.PoolGuiManager
import top.cnuo.idlepool.pool.PoolRepository
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.storage.SqliteStore
import java.util.UUID

class PoolPresenceListener(
    private val plugin: JavaPlugin,
    private val pools: PoolRepository,
    private val gui: PoolGuiManager,
    private val sessions: SessionManager,
    private val store: SqliteStore,
) : Listener {
    private val observedPool = mutableMapOf<UUID, String>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) { if (changedBlock(event.from, event.to)) handlePosition(event.player, event.to) }
    @EventHandler fun onJoin(event: PlayerJoinEvent) {
        store.refreshInboxCount(event.player.uniqueId)
        schedule(event.player)
    }
    @EventHandler fun onWorld(event: PlayerChangedWorldEvent) { observedPool.remove(event.player.uniqueId); schedule(event.player) }
    @EventHandler fun onRespawn(event: PlayerRespawnEvent) { observedPool.remove(event.player.uniqueId); schedule(event.player) }
    @EventHandler fun onDeath(event: PlayerDeathEvent) { observedPool.remove(event.entity.uniqueId); sessions.stop(event.entity, false, false) }
    @EventHandler fun onQuit(event: PlayerQuitEvent) { observedPool.remove(event.player.uniqueId); sessions.stop(event.player, false, false) }

    private fun handlePosition(player: Player, location: Location) {
        val active = sessions.find(player.uniqueId).orElse(null)
        if (active != null) {
            if (!active.pool.region.contains(location)) sessions.stop(player, true)
            observedPool[player.uniqueId] = pools.at(location).map { it.id }.orElse("")
            return
        }
        val current = pools.at(location)
        val currentId = current.map { it.id }.orElse("")
        val previous = observedPool.put(player.uniqueId, currentId)
        if (currentId.isNotBlank() && currentId != previous) Bukkit.getScheduler().runTask(plugin, Runnable {
            val pool = current.orElse(null) ?: return@Runnable
            if (!sessions.isActive(player.uniqueId) && pool.region.contains(player.location)) gui.open(player, pool)
        })
    }

    private fun schedule(player: Player) { Bukkit.getScheduler().runTask(plugin, Runnable { handlePosition(player, player.location) }) }
    private fun changedBlock(from: Location, to: Location) = from.world !== to.world || from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ
}
