package top.cnuo.idlepool.reward

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.activity.ActivityEventRepository
import top.cnuo.idlepool.api.event.IdlePoolRewardPostGrantEvent
import top.cnuo.idlepool.api.event.IdlePoolRewardPreGrantEvent
import top.cnuo.idlepool.integration.EconomyBridge
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.storage.SqliteStore
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.floor

class RewardService(
    private val plugin: JavaPlugin,
    private val plans: RewardPlanRepository,
    private val store: SqliteStore,
    private val economy: EconomyBridge,
    private val activities: ActivityEventRepository,
) {
    fun grantCycle(player: Player, pool: PoolDefinition, sessionSeconds: Long, sequence: Long, pityCount: Long): CycleGrant {
        val plan = plans.find(pool.rewardPlan).orElse(null) ?: run {
            plugin.logger.warning("挂机池 ${pool.id} 引用了不存在的奖励方案 ${pool.rewardPlan}")
            return CycleGrant(0, pityCount, false)
        }
        val eligible = plan.rewards.withIndex().filter { it.value.eligibleForCycle(sessionSeconds) }
        val target = plan.pity.rewardIndex.takeIf { plan.pity.enabled && it in plan.rewards.indices }
        val targetEligible = target != null && eligible.any { it.index == target }
        val pityTriggered = targetEligible && pityCount + 1 >= plan.pity.afterCycles
        val seed = "${player.uniqueId}:${pool.id}:cycle:$sequence"
        val selected = when (plan.selectionMode) {
            SelectionMode.INDEPENDENT -> eligible.filter { DeterministicRewardSelector.roll("$seed:${it.index}") < it.value.chance }.toMutableList()
            SelectionMode.WEIGHTED_ONE -> DeterministicRewardSelector.weightedDraw(eligible, 1, seed).toMutableList()
            SelectionMode.WEIGHTED_MULTIPLE -> DeterministicRewardSelector.weightedDraw(eligible, plan.drawCount, seed).toMutableList()
        }
        if (pityTriggered) {
            val forced = eligible.first { it.index == target }
            selected.removeAll { it.index == target }
            selected.add(0, forced)
        }
        if (plan.selectionMode == SelectionMode.INDEPENDENT) {
            eligible.filter { candidate -> selected.none { it.index == candidate.index } }.forEach { missed ->
                val id = "$seed:${missed.index}"
                store.reserveSettlement(id, player.uniqueId, pool.id, missed.value.type.name, rewardKey(missed.value), amount(missed.value, 1.0))
                    .thenCompose { reserved -> if (reserved) store.completeSettlement(id, false, "chance-miss") else CompletableFuture.completedFuture(null) }
            }
        }
        val multipliers = activities.multipliers(pool.id)
        val granted = selected.sumOf { candidate ->
            tryGrant(player, pool, candidate.value, "$seed:${candidate.index}", pityTriggered && candidate.index == target, multipliers.item, multipliers.money)
        }
        val targetWon = target != null && selected.any { it.index == target }
        val nextPity = plan.pity.nextCount(pityCount, targetEligible, targetWon, pityTriggered)
        return CycleGrant(granted, nextPity, pityTriggered)
    }

    fun grantMilestones(player: Player, pool: PoolDefinition, previous: Long, current: Long, sessionId: String = UUID.randomUUID().toString()): MilestoneGrant {
        val plan = plans.find(pool.rewardPlan).orElse(null) ?: return MilestoneGrant(0, 0, emptyList())
        val multipliers = activities.multipliers(pool.id)
        var eligibleCount = 0
        var granted = 0
        val reached = mutableListOf<Long>()
        plan.rewards.withIndex().forEach { (index, reward) ->
            if (reward.milestoneCrossed(previous, current)) {
                eligibleCount++
                reached += reward.unlockAfter.seconds
                val id = "${player.uniqueId}:${pool.id}:milestone:$sessionId:${reward.unlockAfter.seconds}:$index"
                if (DeterministicRewardSelector.roll(id) < reward.chance) granted += tryGrant(player, pool, reward, id, false, multipliers.item, multipliers.money)
                else store.reserveSettlement(id, player.uniqueId, pool.id, reward.type.name, rewardKey(reward), amount(reward, 1.0))
                    .thenCompose { reserved -> if (reserved) store.completeSettlement(id, false, "chance-miss") else CompletableFuture.completedFuture(null) }
            }
        }
        return MilestoneGrant(eligibleCount, granted, reached)
    }

    private fun tryGrant(
        player: Player,
        pool: PoolDefinition,
        reward: RewardDefinition,
        settlementId: String,
        pity: Boolean,
        itemMultiplier: Double,
        moneyMultiplier: Double,
    ): Int {
        val configuredMultiplier = when (reward.type) {
            RewardType.ITEM -> itemMultiplier
            RewardType.MONEY -> moneyMultiplier
            RewardType.COMMAND -> 1.0
        }
        val event = IdlePoolRewardPreGrantEvent(player, pool, reward, settlementId, pity, configuredMultiplier)
        Bukkit.getPluginManager().callEvent(event)
        val multiplier = event.amountMultiplier.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: configuredMultiplier
        if (event.isCancelled || multiplier <= 0) {
            store.reserveSettlement(settlementId, player.uniqueId, pool.id, reward.type.name, rewardKey(reward), 0.0)
                .thenCompose { reserved -> if (reserved) store.completeSettlement(settlementId, false, "event-cancelled") else CompletableFuture.completedFuture(null) }
            return 0
        }
        return when (reward.type) {
            RewardType.ITEM -> {
                val actual = floor(reward.itemAmount * multiplier).toInt().coerceAtLeast(1)
                store.enqueueItemSettlement(settlementId, player.uniqueId, pool.id, reward.provider, reward.itemId, actual, reward.itemId)
                    .whenComplete { success, _ -> post(player, pool, reward, settlementId, actual.toDouble(), success == true) }
                actual
            }
            RewardType.MONEY -> {
                val actual = reward.moneyAmount * multiplier
                executeOnce(settlementId, player, pool, reward, actual) { economy.deposit(player, actual) }
                1
            }
            RewardType.COMMAND -> {
                executeOnce(settlementId, player, pool, reward, 1.0) {
                    val command = reward.command.replace("{player}", player.name).replace("{uuid}", player.uniqueId.toString()).replace("{pool}", pool.id).removePrefix("/")
                    command.isNotBlank() && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                }
                1
            }
        }
    }

    private fun executeOnce(settlementId: String, player: Player, pool: PoolDefinition, reward: RewardDefinition, actualAmount: Double, action: () -> Boolean) {
        store.reserveSettlement(settlementId, player.uniqueId, pool.id, reward.type.name, rewardKey(reward), actualAmount).whenComplete { reserved, failure ->
            if (failure != null || reserved != true) return@whenComplete
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val success = runCatching(action).getOrElse { plugin.logger.warning("奖励执行失败 $settlementId: ${it.message}"); false }
                store.completeSettlement(settlementId, success, if (success) "executed" else "execution-failed")
                post(player, pool, reward, settlementId, actualAmount, success)
            })
        }
    }

    private fun post(player: Player, pool: PoolDefinition, reward: RewardDefinition, id: String, amount: Double, success: Boolean) {
        val task = Runnable { Bukkit.getPluginManager().callEvent(IdlePoolRewardPostGrantEvent(player, pool, reward, id, amount, success)) }
        if (Bukkit.isPrimaryThread()) task.run() else Bukkit.getScheduler().runTask(plugin, task)
    }

    private fun rewardKey(reward: RewardDefinition) = when (reward.type) {
        RewardType.ITEM -> "${reward.provider}:${reward.itemId}"
        RewardType.MONEY -> "vault"
        RewardType.COMMAND -> reward.command.take(128)
    }
    private fun amount(reward: RewardDefinition, multiplier: Double) = when (reward.type) {
        RewardType.ITEM -> reward.itemAmount * multiplier
        RewardType.MONEY -> reward.moneyAmount * multiplier
        RewardType.COMMAND -> 1.0
    }

    data class CycleGrant(val granted: Int, val pityCount: Long, val pityTriggered: Boolean)
    data class MilestoneGrant(val eligible: Int, val granted: Int, val milestones: List<Long>)
}

internal object DeterministicRewardSelector {
    fun weightedDraw(eligible: List<IndexedValue<RewardDefinition>>, count: Int, seed: String): List<IndexedValue<RewardDefinition>> {
        val remaining = eligible.filter { it.value.weight > 0 }.toMutableList()
        val selected = mutableListOf<IndexedValue<RewardDefinition>>()
        repeat(count.coerceAtMost(remaining.size)) { draw ->
            val total = remaining.sumOf { it.value.weight }
            if (total <= 0) return@repeat
            var cursor = roll("$seed:draw:$draw") / 100.0 * total
            val chosen = remaining.firstOrNull { candidate -> cursor -= candidate.value.weight; cursor < 0 } ?: remaining.last()
            selected += chosen
            remaining.remove(chosen)
        }
        return selected
    }

    fun roll(id: String): Double {
        val bytes = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        val value = ByteBuffer.wrap(bytes).long and Long.MAX_VALUE
        return (value ushr 10).toDouble() / (1L shl 53).toDouble() * 100.0
    }
}
