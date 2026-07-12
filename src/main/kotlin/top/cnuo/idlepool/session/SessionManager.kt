package top.cnuo.idlepool.session

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import top.cnuo.idlepool.gui.InboxGuiManager
import top.cnuo.idlepool.integration.VisualBridge
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.reward.RewardService
import top.cnuo.idlepool.storage.ProgressRecord
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.util.Messages
import top.cnuo.idlepool.util.TimeFormats
import java.time.Instant
import java.util.Optional
import java.util.UUID

class SessionManager(
    private val plugin: JavaPlugin,
    private val store: SqliteStore,
    private val rewards: RewardService,
    private val visuals: VisualBridge,
    private val inbox: InboxGuiManager,
) : AutoCloseable {
    private val active = mutableMapOf<UUID, ActiveSession>()
    private val starting = mutableSetOf<UUID>()
    private var ticker: BukkitTask? = null
    private var checkpointCounter = 0

    fun beginTicking() { ticker = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 20L, 20L) }

    fun start(player: Player, pool: PoolDefinition) {
        val id = player.uniqueId
        when {
            active.containsKey(id) -> Messages.send(player, "session.already-active")
            pool.permission.isNotBlank() && !player.hasPermission(pool.permission) -> Messages.send(player, "session.no-permission")
            pool.maxActivePlayers > 0 && activeCount(pool.id) >= pool.maxActivePlayers -> Messages.send(player, "session.pool-full")
            !starting.add(id) -> Unit
            else -> {
                player.closeInventory(); Messages.send(player, "session.progress-loading")
                store.loadProgress(id, pool.id).whenComplete { progress, failure -> sync { finishStart(id, pool, progress, failure) } }
            }
        }
    }

    private fun finishStart(id: UUID, pool: PoolDefinition, loaded: ProgressRecord?, failure: Throwable?) {
        starting.remove(id)
        val player = Bukkit.getPlayer(id) ?: return
        if (!player.isOnline || failure != null || active.containsKey(id)) return
        if (!pool.enabled || !pool.region.contains(player.location)) { Messages.send(player, "session.no-pool"); return }
        if (pool.maxActivePlayers > 0 && activeCount(pool.id) >= pool.maxActivePlayers) { Messages.send(player, "session.pool-full"); return }
        val session = ActiveSession(id, pool, (loaded ?: ProgressRecord.empty()).discardExpired(Instant.now().epochSecond))
        active[id] = session
        Messages.send(player, "session.started", mapOf("pool" to pool.id))
        updateActionBar(player, session)
    }

    @JvmOverloads
    fun stop(player: Player, leftRegion: Boolean, openInbox: Boolean = true) {
        val session = active.remove(player.uniqueId)
        starting.remove(player.uniqueId)
        if (session == null) return
        save(session)
        Messages.send(player, if (leftRegion) "session.left-region" else "session.stopped", mapOf("session_time" to TimeFormats.clock(session.sessionSeconds)))
        player.sendActionBar(Component.empty())
        if (openInbox && player.isOnline && plugin.config.getBoolean("inbox.open-on-stop", true)) inbox.open(player)
    }

    fun isActive(playerId: UUID) = active.containsKey(playerId)
    fun find(playerId: UUID) = Optional.ofNullable(active[playerId])
    fun activeCount(poolId: String) = active.values.count { it.pool.id == poolId }

    private fun tick() {
        checkpointCounter++
        val checkpoint = plugin.config.getInt("storage.checkpoint-interval-seconds", 30).coerceAtLeast(5)
        active.values.toList().forEach { session ->
            val player = Bukkit.getPlayer(session.playerId)
            if (player == null || !player.isOnline) { active.remove(session.playerId); save(session); return@forEach }
            if (!session.pool.region.contains(player.location)) { stop(player, true); return@forEach }
            val previous = session.sessionSeconds
            session.tick()
            val milestone = rewards.grantMilestones(player, session.pool, previous, session.sessionSeconds, session.sessionId)
            if (milestone.eligible > 0) {
                session.recordMilestone(milestone.granted)
                Messages.send(player, "session.milestone-reached", mapOf(
                    "time" to TimeFormats.clock(session.sessionSeconds), "earned" to milestone.granted.toString(),
                ))
            }
            while (session.cycleReady()) {
                val granted = rewards.grantCycle(player, session.pool, session.sessionSeconds, session.rewardSequence)
                session.completeCycle(granted)
            }
            updateActionBar(player, session)
            if (checkpointCounter >= checkpoint) save(session)
        }
        if (checkpointCounter >= checkpoint) checkpointCounter = 0
    }

    private fun updateActionBar(player: Player, session: ActiveSession) {
        if (!plugin.config.getBoolean("actionbar.enabled", true)) return
        val next = (session.pool.rewardCycle.seconds - session.progressSeconds).coerceAtLeast(0)
        var format = Messages.raw("actionbar.format")
        listOf("clock", "gift", "coin").forEach { token ->
            format = format.replace(":$token:", visuals.fontImage(plugin.config.getString("itemsadder.font-images.$token", "")))
        }
        player.sendActionBar(Messages.parse(format, mapOf(
            "pool" to session.pool.id, "session_time" to TimeFormats.clock(session.sessionSeconds),
            "total_time" to TimeFormats.clock(session.totalSeconds), "next_reward" to TimeFormats.clock(next),
            "earned" to session.earned.toString(), "players" to activeCount(session.pool.id).toString(),
        )))
    }

    private fun save(session: ActiveSession) {
        val expires = Instant.now().plus(session.pool.progressRetention).epochSecond
        store.saveProgress(session.playerId, session.pool.id, session.snapshot(expires))
    }

    override fun close() {
        ticker?.cancel(); active.values.forEach(::save); active.clear(); starting.clear()
    }

    private fun sync(action: () -> Unit) { Bukkit.getScheduler().runTask(plugin, Runnable(action)) }
}
