package top.cnuo.idlepool.activity

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

data class ActivityEventDefinition(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val start: Instant?,
    val end: Instant?,
    val pools: Set<String>,
    val progressMultiplier: Double,
    val itemMultiplier: Double,
    val moneyMultiplier: Double,
) {
    fun activeAt(now: Instant, poolId: String) = enabled &&
        (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end)) &&
        (pools.isEmpty() || "*" in pools || poolId.lowercase(Locale.ROOT) in pools)
}

data class EventMultipliers(
    val progress: Double = 1.0,
    val item: Double = 1.0,
    val money: Double = 1.0,
    val activeNames: List<String> = emptyList(),
) {
    val label get() = activeNames.joinToString(" + ")
}

class ActivityEventRepository(private val plugin: JavaPlugin) : AutoCloseable {
    private val file = File(plugin.dataFolder, "events.yml")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "IdlePool-EventsConfig").apply { isDaemon = true } }
    @Volatile private var events: Map<String, ActivityEventDefinition> = emptyMap()
    @Volatile private var zone: ZoneId = ZoneId.systemDefault()

    fun reload() {
        val yaml = YamlConfiguration.loadConfiguration(file)
        zone = runCatching { ZoneId.of(yaml.getString("time-zone", ZoneId.systemDefault().id)!!) }.getOrElse {
            plugin.logger.warning("events.yml 时区无效，已使用系统时区：${it.message}")
            ZoneId.systemDefault()
        }
        val root = yaml.getConfigurationSection("events") ?: run { events = emptyMap(); return }
        events = root.getKeys(false).mapNotNull { id ->
            val section = root.getConfigurationSection(id) ?: return@mapNotNull null
            runCatching {
                id.lowercase(Locale.ROOT) to ActivityEventDefinition(
                    id,
                    section.getString("display-name", id) ?: id,
                    section.getBoolean("enabled", false),
                    parseInstant(section.getString("start")),
                    parseInstant(section.getString("end")),
                    section.getStringList("pools").map { it.lowercase(Locale.ROOT) }.toSet(),
                    multiplier(section.getDouble("progress-multiplier", 1.0)),
                    multiplier(section.getDouble("item-multiplier", 1.0)),
                    multiplier(section.getDouble("money-multiplier", 1.0)),
                )
            }.onFailure { plugin.logger.log(Level.SEVERE, "无法加载倍率活动 $id", it) }.getOrNull()
        }.toMap()
        plugin.logger.info("已加载 ${events.size} 个挂机倍率活动。")
    }

    fun all() = events.values.sortedBy(ActivityEventDefinition::id)
    fun find(id: String) = Optional.ofNullable(events[id.lowercase(Locale.ROOT)])
    fun active(poolId: String, now: Instant = Instant.now()) = all().filter { it.activeAt(now, poolId) }
    fun multipliers(poolId: String, now: Instant = Instant.now()): EventMultipliers {
        val active = active(poolId, now)
        return EventMultipliers(
            active.fold(1.0) { total, event -> (total * event.progressMultiplier).coerceAtMost(100.0) },
            active.fold(1.0) { total, event -> (total * event.itemMultiplier).coerceAtMost(100.0) },
            active.fold(1.0) { total, event -> (total * event.moneyMultiplier).coerceAtMost(100.0) },
            active.map(ActivityEventDefinition::displayName),
        )
    }

    fun setEnabled(id: String, enabled: Boolean, startNow: Boolean = false): CompletableFuture<Void> = CompletableFuture.runAsync({
        val yaml = YamlConfiguration.loadConfiguration(file)
        val path = "events.$id"
        require(yaml.contains(path)) { "Unknown event: $id" }
        yaml.set("$path.enabled", enabled)
        if (enabled && startNow) {
            val now = LocalDateTime.now(zone)
            yaml.set("$path.start", FORMATTER.format(now))
            val end = runCatching { parseInstant(yaml.getString("$path.end")) }.getOrNull()
            if (end == null || !end.isAfter(Instant.now())) yaml.set("$path.end", FORMATTER.format(now.plusHours(1)))
        }
        yaml.save(file)
    }, executor).thenRun(::reload)

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank() || value.equals("none", true)) return null
        return runCatching { Instant.parse(value) }.getOrElse { LocalDateTime.parse(value, FORMATTER).atZone(zone).toInstant() }
    }
    private fun multiplier(value: Double) = value.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 1.0
    override fun close() { executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS) }

    companion object { private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
}
