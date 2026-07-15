package top.cnuo.idlepool.reward

import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.Locale

enum class RewardType { ITEM, MONEY, COMMAND }

enum class SelectionMode {
    INDEPENDENT, WEIGHTED_ONE, WEIGHTED_MULTIPLE;

    companion object {
        fun parse(input: String?) = when (input?.trim()?.lowercase(Locale.ROOT) ?: "independent") {
            "independent" -> INDEPENDENT
            "weighted-one", "weighted_one" -> WEIGHTED_ONE
            "weighted-multiple", "weighted_multiple" -> WEIGHTED_MULTIPLE
            else -> throw IllegalArgumentException("Unsupported selection mode: $input")
        }
    }
}

data class PityConfig(
    val enabled: Boolean = false,
    val afterCycles: Int = 0,
    val rewardIndex: Int = -1,
    val resetOnWin: Boolean = true,
) {
    init { require(!enabled || afterCycles > 0) { "Pity cycles must be positive" } }

    fun nextCount(current: Long, targetEligible: Boolean, targetWon: Boolean, pityTriggered: Boolean): Long = when {
        !enabled || !targetEligible -> current
        pityTriggered || targetWon && resetOnWin -> 0
        else -> current + 1
    }
}

enum class RewardTrigger {
    CYCLE, SESSION_MILESTONE;

    companion object {
        @JvmStatic
        fun parse(input: String?): RewardTrigger = when (input?.trim()?.lowercase(Locale.ROOT) ?: "cycle") {
            "cycle" -> CYCLE
            "milestone", "session-milestone", "session_milestone" -> SESSION_MILESTONE
            else -> throw IllegalArgumentException("Unsupported reward trigger: $input")
        }
    }
}

data class RewardDefinition(
    val type: RewardType,
    val provider: String,
    val itemId: String,
    val itemAmount: Int,
    val moneyAmount: Double,
    val command: String,
    val chance: Double,
    val weight: Double,
    val trigger: RewardTrigger,
    val unlockAfter: Duration,
) {
    init {
        require(!unlockAfter.isNegative) { "Reward unlock time cannot be negative" }
        require(weight.isFinite() && weight >= 0) { "Reward weight cannot be negative" }
        require(trigger != RewardTrigger.SESSION_MILESTONE || !unlockAfter.isZero) {
            "Session milestone reward requires a positive unlock time"
        }
    }

    fun eligibleForCycle(sessionSeconds: Long) =
        trigger == RewardTrigger.CYCLE && sessionSeconds >= unlockAfter.seconds

    fun milestoneCrossed(previousSessionSeconds: Long, sessionSeconds: Long) =
        trigger == RewardTrigger.SESSION_MILESTONE &&
            previousSessionSeconds < unlockAfter.seconds && sessionSeconds >= unlockAfter.seconds

    fun type() = type
    fun provider() = provider
    fun itemId() = itemId
    fun itemAmount() = itemAmount
    fun moneyAmount() = moneyAmount
    fun command() = command
    fun chance() = chance
    fun trigger() = trigger
    fun unlockAfter() = unlockAfter
}

data class RewardPlan(
    val id: String,
    val rewards: List<RewardDefinition>,
    val selectionMode: SelectionMode = SelectionMode.INDEPENDENT,
    val drawCount: Int = 1,
    val pity: PityConfig = PityConfig(),
) {
    init { require(drawCount > 0) { "Draw count must be positive" } }
    fun id() = id
    fun rewards() = rewards
}

data class IdentifiedItem(val provider: String, val itemId: String, val amount: Int, val snapshot: ItemStack?) {
    fun requiresSnapshot() = snapshot != null
    fun provider() = provider
    fun itemId() = itemId
    fun amount() = amount
    fun snapshot() = snapshot
}
