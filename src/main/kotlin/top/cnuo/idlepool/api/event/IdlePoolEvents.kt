package top.cnuo.idlepool.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.reward.RewardDefinition
import top.cnuo.idlepool.storage.InboxEntry

class IdlePoolStartEvent(val player: Player, val pool: PoolDefinition) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

enum class IdlePoolStopReason { COMMAND, LEFT_REGION, QUIT, DEATH, DISABLE }

class IdlePoolStopEvent(val player: Player, val pool: PoolDefinition, val reason: IdlePoolStopReason, val sessionSeconds: Long) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class IdlePoolCycleCompleteEvent(val player: Player, val pool: PoolDefinition, val sequence: Long, val granted: Int, val pityTriggered: Boolean) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class IdlePoolMilestoneEvent(val player: Player, val pool: PoolDefinition, val milestoneSeconds: Long, val granted: Int) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class IdlePoolRewardPreGrantEvent(
    val player: Player,
    val pool: PoolDefinition,
    val reward: RewardDefinition,
    val settlementId: String,
    val pity: Boolean,
    var amountMultiplier: Double,
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class IdlePoolRewardPostGrantEvent(
    val player: Player,
    val pool: PoolDefinition,
    val reward: RewardDefinition,
    val settlementId: String,
    val amount: Double,
    val success: Boolean,
) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class IdlePoolInboxClaimEvent(val player: Player, val entry: InboxEntry, val amount: Int) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { private val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
