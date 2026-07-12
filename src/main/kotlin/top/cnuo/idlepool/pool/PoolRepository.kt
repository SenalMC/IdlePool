package top.cnuo.idlepool.pool

import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.util.DurationParser
import java.io.File
import java.time.Duration
import java.util.Optional
import java.util.logging.Level

class PoolRepository(private val plugin: JavaPlugin) {
    @Volatile private var pools: List<PoolDefinition> = emptyList()

    fun reload(defaultRetention: Duration) {
        val root = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "pools.yml")).getConfigurationSection("pools")
            ?: run { pools = emptyList(); return }
        pools = root.getKeys(false).mapNotNull { id ->
            val section = root.getConfigurationSection(id) ?: return@mapNotNull null
            try { parse(id, section, defaultRetention) }
            catch (exception: RuntimeException) { plugin.logger.log(Level.SEVERE, "无法加载挂机池 $id", exception); null }
        }
        plugin.logger.info("已加载 ${pools.size} 个挂机池。")
    }

    fun all() = pools
    fun at(location: Location) = Optional.ofNullable(pools.firstOrNull { it.enabled && it.region.contains(location) })
    fun byId(id: String) = Optional.ofNullable(pools.firstOrNull { it.id.equals(id, true) })

    private fun parse(id: String, section: ConfigurationSection, defaultRetention: Duration): PoolDefinition {
        val region = requireSection(section, "region")
        val min = requireSection(region, "min")
        val max = requireSection(region, "max")
        val gui = requireSection(section, "gui")
        return PoolDefinition(
            id, section.getBoolean("enabled", true), section.getString("display-name", id) ?: id,
            CuboidRegion(require(section, "world"), min.getInt("x"), min.getInt("y"), min.getInt("z"), max.getInt("x"), max.getInt("y"), max.getInt("z")),
            section.getString("permission", "idlepool.use") ?: "idlepool.use",
            section.getInt("max-active-players", 0).coerceAtLeast(0),
            DurationParser.parse(section.getString("reward-cycle", "10m")),
            if (section.contains("progress-retention")) DurationParser.parse(section.getString("progress-retention", "7d")) else defaultRetention,
            section.getString("reward-plan", "basic") ?: "basic",
            PoolVisuals(gui.getString("start-item", "") ?: "", gui.getString("info-item", "") ?: "", gui.getString("rewards-item", "") ?: ""),
        )
    }

    private fun require(section: ConfigurationSection, path: String) = section.getString(path)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("Missing value: ${section.currentPath}.$path")
    private fun requireSection(section: ConfigurationSection, path: String) = section.getConfigurationSection(path)
        ?: throw IllegalArgumentException("Missing section: ${section.currentPath}.$path")
}
