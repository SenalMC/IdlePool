package top.cnuo.idlepool.reward

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.integration.EconomyBridge
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.storage.SqliteStore
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

class RewardService(
    private val plugin: JavaPlugin,
    private val plans: RewardPlanRepository,
    private val store: SqliteStore,
    private val economy: EconomyBridge,
) {
    fun grantCycle(player: Player, pool: PoolDefinition, sessionSeconds: Long): Int =
        grantCycle(player, pool, sessionSeconds, System.nanoTime())

    fun grantCycle(player: Player, pool: PoolDefinition, sessionSeconds: Long, sequence: Long): Int {
        val plan = plans.find(pool.rewardPlan).orElse(null) ?: run {
            plugin.logger.warning("挂机池 ${pool.id} 引用了不存在的奖励方案 ${pool.rewardPlan}"); return 0
        }
        return plan.rewards.withIndex().sumOf { (index, reward) ->
            if (!reward.eligibleForCycle(sessionSeconds)) 0
            else tryGrant(player, pool, reward, "${player.uniqueId}:${pool.id}:cycle:$sequence:$index")
        }
    }

    fun grantMilestones(player: Player, pool: PoolDefinition, previous: Long, current: Long): MilestoneGrant =
        grantMilestones(player, pool, previous, current, UUID.randomUUID().toString())

    fun grantMilestones(player: Player, pool: PoolDefinition, previous: Long, current: Long, sessionId: String): MilestoneGrant {
        val plan = plans.find(pool.rewardPlan).orElse(null) ?: return MilestoneGrant(0, 0)
        var eligible = 0
        var granted = 0
        plan.rewards.withIndex().forEach { (index, reward) ->
            if (reward.milestoneCrossed(previous, current)) {
                eligible++
                granted += tryGrant(player, pool, reward, "${player.uniqueId}:${pool.id}:milestone:$sessionId:${reward.unlockAfter.seconds}:$index")
            }
        }
        return MilestoneGrant(eligible, granted)
    }

    private fun tryGrant(player: Player, pool: PoolDefinition, reward: RewardDefinition, settlementId: String): Int {
        if (stableRoll(settlementId) >= reward.chance) {
            store.reserveSettlement(settlementId, player.uniqueId, pool.id, reward.type.name, rewardKey(reward), amount(reward))
                .thenCompose { reserved -> if (reserved) store.completeSettlement(settlementId, false, "chance-miss") else java.util.concurrent.CompletableFuture.completedFuture(null) }
            return 0
        }
        return when (reward.type) {
            RewardType.ITEM -> {
                store.enqueueItemSettlement(settlementId, player.uniqueId, pool.id, reward.provider, reward.itemId, reward.itemAmount, reward.itemId)
                reward.itemAmount
            }
            RewardType.MONEY -> {
                executeOnce(settlementId, player, pool, reward) { economy.deposit(player, reward.moneyAmount) }
                1
            }
            RewardType.COMMAND -> {
                executeOnce(settlementId, player, pool, reward) {
                    val command = reward.command
                        .replace("{player}", player.name).replace("{uuid}", player.uniqueId.toString()).replace("{pool}", pool.id)
                        .removePrefix("/")
                    command.isNotBlank() && Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                }
                1
            }
        }
    }

    private fun executeOnce(settlementId: String, player: Player, pool: PoolDefinition, reward: RewardDefinition, action: () -> Boolean) {
        store.reserveSettlement(settlementId, player.uniqueId, pool.id, reward.type.name, rewardKey(reward), amount(reward))
            .whenComplete { reserved, failure ->
                if (failure != null || reserved != true) return@whenComplete
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val success = runCatching(action).getOrElse { plugin.logger.warning("奖励执行失败 $settlementId: ${it.message}"); false }
                    store.completeSettlement(settlementId, success, if (success) "executed" else "execution-failed")
                })
            }
    }

    private fun rewardKey(reward: RewardDefinition) = when (reward.type) {
        RewardType.ITEM -> "${reward.provider}:${reward.itemId}"
        RewardType.MONEY -> "vault"
        RewardType.COMMAND -> reward.command.take(128)
    }
    private fun amount(reward: RewardDefinition) = when (reward.type) {
        RewardType.ITEM -> reward.itemAmount.toDouble()
        RewardType.MONEY -> reward.moneyAmount
        RewardType.COMMAND -> 1.0
    }

    private fun stableRoll(id: String): Double {
        val bytes = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        val value = ByteBuffer.wrap(bytes).long and Long.MAX_VALUE
        return value.toDouble() / Long.MAX_VALUE.toDouble() * 100.0
    }

    data class MilestoneGrant(val eligible: Int, val granted: Int) {
        fun eligible() = eligible
        fun granted() = granted
    }
}
