package top.cnuo.idlepool.reward

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.util.DurationParser
import java.io.File
import java.time.Duration
import java.util.Locale
import java.util.Optional
import java.util.logging.Level

class RewardPlanRepository(private val plugin: JavaPlugin) {
    @Volatile private var plans: Map<String, RewardPlan> = emptyMap()

    fun reload() {
        val root = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "rewards.yml")).getConfigurationSection("reward-plans")
            ?: run { plans = emptyMap(); return }
        plans = root.getKeys(false).mapNotNull { id ->
            val section = root.getConfigurationSection(id) ?: return@mapNotNull null
            try { id.lowercase(Locale.ROOT) to RewardPlan(id, section.getMapList("rewards").map(::parse)) }
            catch (exception: RuntimeException) { plugin.logger.log(Level.SEVERE, "无法加载奖励方案 $id", exception); null }
        }.toMap()
        plugin.logger.info("已加载 ${plans.size} 个奖励方案。")
    }

    fun find(id: String) = Optional.ofNullable(plans[id.lowercase(Locale.ROOT)])
    fun all() = plans.values.sortedBy { it.component1() }

    private fun parse(raw: Map<*, *>): RewardDefinition {
        val type = RewardType.valueOf(string(raw, "type", "item").uppercase(Locale.ROOT))
        val chance = number(raw, "chance", 100.0).toDouble()
        require(chance.isFinite() && chance in 0.0..100.0) { "Reward chance must be between 0 and 100" }
        val trigger = RewardTrigger.parse(string(raw, "trigger", "cycle"))
        val unlock = duration(raw["unlock-after"])
        return when (type) {
            RewardType.ITEM -> RewardDefinition(type, string(raw, "provider", "vanilla").lowercase(), string(raw, "id", "STONE"), number(raw, "amount", 1).toInt().coerceAtLeast(1), 0.0, "", chance, trigger, unlock)
            RewardType.MONEY -> RewardDefinition(type, "vault", "", 0, number(raw, "amount", 0).toDouble().coerceAtLeast(0.0), "", chance, trigger, unlock)
            RewardType.COMMAND -> RewardDefinition(type, "command", "", 0, 0.0, string(raw, "command", ""), chance, trigger, unlock)
        }
    }

    private fun duration(value: Any?): Duration {
        val text = value?.toString()?.trim() ?: return Duration.ZERO
        return if (text == "0" || text.equals("0s", true) || text.equals("none", true)) Duration.ZERO else DurationParser.parse(text)
    }
    private fun string(map: Map<*, *>, key: String, fallback: String) = map[key]?.toString() ?: fallback
    private fun number(map: Map<*, *>, key: String, fallback: Number) = (map[key] as? Number) ?: map[key]?.toString()?.toDoubleOrNull() ?: fallback
}
