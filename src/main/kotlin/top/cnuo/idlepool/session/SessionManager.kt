package top.cnuo.idlepool.session

import net.kyori.adventure.text.Component
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import top.cnuo.idlepool.gui.InboxGuiManager
import top.cnuo.idlepool.activity.ActivityEventRepository
import top.cnuo.idlepool.api.event.*
import top.cnuo.idlepool.integration.VisualBridge
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.reward.RewardService
import top.cnuo.idlepool.storage.ProgressRecord
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.storage.SessionCheckpoint
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
    private val activities: ActivityEventRepository,
) : AutoCloseable {
    private val active = mutableMapOf<UUID, ActiveSession>()
    private val starting = mutableSetOf<UUID>()
    private var ticker: BukkitTask? = null
    private var checkpointCounter = 0
    private val bossBars = mutableMapOf<UUID, BossBar>()

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
        val startEvent = IdlePoolStartEvent(player, pool)
        Bukkit.getPluginManager().callEvent(startEvent)
        if (startEvent.isCancelled) { Messages.send(player, "session.start-cancelled"); return }
        val session = ActiveSession(id, pool, (loaded ?: ProgressRecord.empty()).discardExpired(Instant.now().epochSecond))
        active[id] = session
        Messages.send(player, "session.started", mapOf("pool" to pool.id))
        updateActionBar(player, session)
    }

    @JvmOverloads
    fun stop(player: Player, leftRegion: Boolean, openInbox: Boolean = true, reason: IdlePoolStopReason = if (leftRegion) IdlePoolStopReason.LEFT_REGION else IdlePoolStopReason.COMMAND) {
        val session = active.remove(player.uniqueId)
        starting.remove(player.uniqueId)
        if (session == null) return
        save(session, player.name)
        Bukkit.getPluginManager().callEvent(IdlePoolStopEvent(player, session.pool, reason, session.sessionSeconds))
        Messages.send(player, if (leftRegion) "session.left-region" else "session.stopped", mapOf("session_time" to TimeFormats.clock(session.sessionSeconds)))
        player.sendActionBar(Component.empty())
        bossBars.remove(player.uniqueId)?.let(player::hideBossBar)
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
            if (player == null || !player.isOnline) { active.remove(session.playerId); save(session, player?.name); return@forEach }
            if (!session.pool.region.contains(player.location)) { stop(player, true); return@forEach }
            val previous = session.sessionSeconds
            val multipliers = activities.multipliers(session.pool.id)
            session.tick(multipliers.progress)
            val milestone = rewards.grantMilestones(player, session.pool, previous, session.sessionSeconds, session.sessionId)
            if (milestone.eligible > 0) {
                session.recordMilestone(milestone.granted)
                milestone.milestones.forEach { seconds -> Bukkit.getPluginManager().callEvent(IdlePoolMilestoneEvent(player, session.pool, seconds, milestone.granted)) }
                Messages.send(player, "session.milestone-reached", mapOf(
                    "time" to TimeFormats.clock(session.sessionSeconds), "earned" to milestone.granted.toString(),
                ))
            }
            while (session.cycleReady()) {
                val sequence = session.rewardSequence
                val result = rewards.grantCycle(player, session.pool, session.sessionSeconds, sequence, session.pityCount)
                session.completeCycle(result.granted, result.pityCount)
                Bukkit.getPluginManager().callEvent(IdlePoolCycleCompleteEvent(player, session.pool, sequence, result.granted, result.pityTriggered))
                if (result.pityTriggered) Messages.send(player, "session.pity-triggered")
            }
            updateActionBar(player, session)
            updateBossBar(player, session, multipliers.label, multipliers.progress)
            if (checkpointCounter >= checkpoint) save(session, player.name)
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
        val multipliers = activities.multipliers(session.pool.id)
        player.sendActionBar(Messages.parse(format, mapOf(
            "pool" to session.pool.id, "session_time" to TimeFormats.clock(session.sessionSeconds),
            "total_time" to TimeFormats.clock(session.totalSeconds), "next_reward" to TimeFormats.clock(next),
            "earned" to session.earned.toString(), "players" to activeCount(session.pool.id).toString(),
            "event" to multipliers.label, "multiplier" to formatMultiplier(multipliers.progress),
        )))
    }

    private fun updateBossBar(player: Player, session: ActiveSession, event: String, multiplier: Double) {
        if (!plugin.config.getBoolean("bossbar.enabled", false)) {
            bossBars.remove(player.uniqueId)?.let(player::hideBossBar)
            return
        }
        val color = runCatching { BossBar.Color.valueOf(plugin.config.getString("bossbar.color", "YELLOW")!!.uppercase()) }.getOrDefault(BossBar.Color.YELLOW)
        val overlay = runCatching { BossBar.Overlay.valueOf(plugin.config.getString("bossbar.overlay", "PROGRESS")!!.uppercase()) }.getOrDefault(BossBar.Overlay.PROGRESS)
        val progress = (session.progressSeconds.toFloat() / session.pool.rewardCycle.seconds.toFloat()).coerceIn(0f, 1f)
        val title = Messages.parse(Messages.raw("bossbar.format"), mapOf(
            "pool" to session.pool.id, "session_time" to TimeFormats.clock(session.sessionSeconds),
            "next_reward" to TimeFormats.clock((session.pool.rewardCycle.seconds - session.progressSeconds).coerceAtLeast(0)),
            "earned" to session.earned.toString(), "event" to event, "multiplier" to formatMultiplier(multiplier),
        ))
        val bar = bossBars[player.uniqueId]
        if (bar == null) {
            BossBar.bossBar(title, progress, color, overlay).also { bossBars[player.uniqueId] = it; player.showBossBar(it) }
        } else { bar.name(title); bar.progress(progress); bar.color(color); bar.overlay(overlay) }
    }

    private fun save(session: ActiveSession, playerName: String? = null) {
        val expires = Instant.now().plus(session.pool.progressRetention).epochSecond
        store.saveProgress(session.playerId, session.pool.id, session.snapshot(expires))
        store.recordSessionCheckpoint(SessionCheckpoint(
            session.sessionId, session.playerId, playerName ?: Bukkit.getOfflinePlayer(session.playerId).name ?: session.playerId.toString().take(8),
            session.pool.id, session.sessionSeconds, session.completedCycles, session.earned,
        ))
    }

    override fun close() {
        ticker?.cancel()
        active.values.forEach { session ->
            Bukkit.getPlayer(session.playerId)?.let { Bukkit.getPluginManager().callEvent(IdlePoolStopEvent(it, session.pool, IdlePoolStopReason.DISABLE, session.sessionSeconds)) }
            save(session)
        }
        bossBars.forEach { (id, bar) -> Bukkit.getPlayer(id)?.hideBossBar(bar) }
        bossBars.clear(); active.clear(); starting.clear()
    }

    private fun sync(action: () -> Unit) { Bukkit.getScheduler().runTask(plugin, Runnable(action)) }
    private fun formatMultiplier(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
}
