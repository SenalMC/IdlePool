package top.cnuo.idlepool.diagnostic

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.integration.EconomyBridge
import top.cnuo.idlepool.integration.VisualBridge
import top.cnuo.idlepool.pool.PoolRepository
import top.cnuo.idlepool.reward.*
import top.cnuo.idlepool.update.UpdateChecker
import top.cnuo.idlepool.util.Messages

class PluginInfoService(private val plugin: JavaPlugin, private val visuals: VisualBridge, private val updates: UpdateChecker) {
    fun show(sender: CommandSender) {
        Messages.send(sender, "info.header"); Messages.send(sender, "info.version", mapOf("version" to plugin.pluginMeta.version))
        Messages.send(sender, "info.server-version", mapOf("version" to Bukkit.getVersion())); Messages.send(sender, "info.itemsadder-status", mapOf("status" to visuals.status()))
        Messages.send(sender, "info.project-url", mapOf("url" to UpdateChecker.PROJECT_URL)); Messages.send(sender, "info.footer")
        val result = updates.result()
        when (result.state) {
            UpdateChecker.State.UPDATE_AVAILABLE -> { Messages.send(sender, "info.update-available", mapOf("version" to result.remoteVersion)); Messages.send(sender, "info.current-version", mapOf("version" to updates.currentVersion())); Messages.send(sender, "info.update-url", mapOf("url" to UpdateChecker.RELEASES_URL)) }
            UpdateChecker.State.UP_TO_DATE -> Messages.send(sender, "info.up-to-date", mapOf("version" to result.remoteVersion))
            UpdateChecker.State.ERROR -> Messages.send(sender, "info.update-error", mapOf("error" to result.message))
            UpdateChecker.State.CHECKING -> Messages.send(sender, "info.checking")
            UpdateChecker.State.DISABLED -> Messages.send(sender, "info.disabled")
            UpdateChecker.State.NOT_CHECKED -> Messages.send(sender, "info.not-checked")
        }
    }
}

class DoctorService(
    private val pools: PoolRepository,
    private val plans: RewardPlanRepository,
    private val visuals: VisualBridge,
    private val economy: EconomyBridge,
) {
    fun report(sender: CommandSender) {
        val result = Result(sender); Messages.send(sender, "doctor.header")
        if (pools.all().isEmpty()) result.error("doctor.no-pools") else result.ok("doctor.pools-loaded", mapOf("count" to pools.all().size.toString()))
        val enabled = pools.all().count { it.enabled }
        if (enabled == 0) result.warning("doctor.no-enabled-pools") else result.ok("doctor.enabled-pools", mapOf("count" to enabled.toString()))
        pools.all().filter { plans.find(it.rewardPlan).isEmpty }.forEach { result.error("doctor.missing-plan", mapOf("pool" to it.id, "plan" to it.rewardPlan)) }
        if (visuals.available()) result.ok("doctor.itemsadder-ready") else result.warning("doctor.itemsadder-unavailable")
        plans.all().forEach { plan ->
            if (plan.rewards.isEmpty()) result.warning("doctor.empty-plan", mapOf("plan" to plan.id))
            plan.rewards.forEach { validate(result, plan, it) }
        }
        Messages.send(sender, "doctor.separator")
        if (result.errors == 0) Messages.send(sender, "doctor.success", mapOf("warnings" to result.warnings.toString())) else Messages.send(sender, "doctor.failure", mapOf("errors" to result.errors.toString()))
    }
    private fun validate(result: Result, plan: RewardPlan, reward: RewardDefinition) {
        if (reward.type == RewardType.MONEY && !economy.available()) { result.warning("doctor.money-without-vault", mapOf("plan" to plan.id)); return }
        if (reward.type != RewardType.ITEM) return
        val provider = reward.provider.lowercase()
        if (provider == "vanilla" && Material.matchMaterial(reward.itemId) == null) result.error("doctor.invalid-vanilla-item", mapOf("plan" to plan.id, "item" to reward.itemId))
        val required = mapOf("itemsadder" to "ItemsAdder", "mythicmobs" to "MythicMobs", "mmoitems" to "MMOItems", "slimefun" to "Slimefun")[provider]
        if (required != null && !Bukkit.getPluginManager().isPluginEnabled(required)) result.warning("doctor.provider-missing", mapOf("plan" to plan.id, "provider" to provider, "plugin" to required))
        if (provider == "itemsadder" && visuals.available() && visuals.customItem(reward.itemId).isEmpty) result.error("doctor.itemsadder-item-missing", mapOf("item" to reward.itemId))
    }
    private class Result(private val sender: CommandSender) {
        var warnings = 0; var errors = 0
        fun ok(key: String, values: Map<String,String> = emptyMap()) = sender.sendMessage(Messages.get(key, values))
        fun warning(key: String, values: Map<String,String> = emptyMap()) { warnings++; sender.sendMessage(Messages.get(key, values)) }
        fun error(key: String, values: Map<String,String> = emptyMap()) { errors++; sender.sendMessage(Messages.get(key, values)) }
    }
}
