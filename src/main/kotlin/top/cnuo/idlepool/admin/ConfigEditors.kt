package top.cnuo.idlepool.admin

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import top.cnuo.idlepool.util.Messages
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class PoolConfigEditor(private val plugin: JavaPlugin) : AutoCloseable {
    private val file = File(plugin.dataFolder, "pools.yml")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "IdlePool-PoolConfig").apply { isDaemon = true } }

    fun set(poolId: String, path: String, value: Any?) = edit { it.set("pools.$poolId.$path", value) }
    fun setPosition(poolId: String, corner: String, location: Location) = edit { yaml ->
        val root = "pools.$poolId"
        yaml.set("$root.world", location.world.name)
        yaml.set("$root.region.$corner.x", location.blockX); yaml.set("$root.region.$corner.y", location.blockY); yaml.set("$root.region.$corner.z", location.blockZ)
    }
    fun createPool(poolId: String, location: Location) = edit { yaml ->
        val root = "pools.$poolId"
        require(!yaml.contains(root)) { Messages.raw("admin.error.pool-exists") }
        yaml.set("$root.enabled", false); yaml.set("$root.display-name", "<gold>$poolId"); yaml.set("$root.world", location.world.name)
        listOf("min", "max").forEach { corner ->
            yaml.set("$root.region.$corner.x", location.blockX); yaml.set("$root.region.$corner.y", location.blockY); yaml.set("$root.region.$corner.z", location.blockZ)
        }
        yaml.set("$root.permission", "idlepool.use"); yaml.set("$root.max-active-players", 0); yaml.set("$root.reward-cycle", "10m")
        yaml.set("$root.progress-retention", "7d"); yaml.set("$root.reward-plan", "basic")
        yaml.set("$root.gui.start-item", "idlepool:start_button"); yaml.set("$root.gui.info-item", "idlepool:info_button"); yaml.set("$root.gui.rewards-item", "idlepool:rewards_button")
    }
    fun copyPool(source: String, target: String) = edit { yaml ->
        require(!yaml.contains("pools.$target")) { Messages.raw("admin.error.pool-exists") }
        val sourceSection = yaml.getConfigurationSection("pools.$source") ?: throw IllegalArgumentException(Messages.raw("admin.error.pool-missing"))
        yaml.set("pools.$target", sectionMap(sourceSection)); yaml.set("pools.$target.display-name", "<gold>$target"); yaml.set("pools.$target.enabled", false)
    }
    fun deletePool(poolId: String) = edit { yaml -> yaml.set("pools.$poolId", null) }

    private fun edit(action: (YamlConfiguration) -> Unit): CompletableFuture<Void> = CompletableFuture.runAsync({
        val yaml = YamlConfiguration.loadConfiguration(file); action(yaml)
        try { yaml.save(file) } catch (exception: Exception) { plugin.logger.log(Level.SEVERE, "无法保存 pools.yml", exception); throw IllegalStateException(exception) }
    }, executor)
    private fun sectionMap(section: ConfigurationSection): Map<String, Any?> = section.getKeys(false).associateWith { key ->
        section.getConfigurationSection(key)?.let(::sectionMap) ?: section.get(key)
    }
    override fun close() { executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS) }
}

class RewardConfigEditor(private val plugin: JavaPlugin) : AutoCloseable {
    private val rewardsFile = File(plugin.dataFolder, "rewards.yml")
    private val snapshotsFile = File(plugin.dataFolder, "reward-snapshots.yml")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "IdlePool-RewardConfig").apply { isDaemon = true } }

    fun createPlan(id: String) = edit { yaml -> require(!yaml.contains(root(id))) { Messages.raw("admin.error.plan-exists") }; yaml.set("${root(id)}.selection-mode", "independent"); yaml.set("${root(id)}.rewards", emptyList<Any>()) }
    fun copyPlan(source: String, target: String) = edit { yaml ->
        require(!yaml.contains(root(target))) { Messages.raw("admin.error.plan-exists") }
        val section = yaml.getConfigurationSection(root(source)) ?: throw IllegalArgumentException(Messages.raw("admin.error.plan-missing", mapOf("plan" to source)))
        yaml.set(root(target), sectionMap(section))
    }
    fun deletePlan(id: String) = edit { it.set(root(id), null) }
    fun addItem(plan: String, provider: String, itemId: String, amount: Int) = append(plan, base("item").apply { put("provider", provider); put("id", itemId); put("amount", amount.coerceAtLeast(1)) })
    fun addCommand(plan: String, command: String) = append(plan, base("command").apply { put("command", command) })
    fun addMoney(plan: String, amount: Double) = append(plan, base("money").apply { put("amount", amount.coerceAtLeast(0.0)) })
    fun addSnapshot(plan: String, snapshotId: String, encoded: String, amount: Int): CompletableFuture<Void> = CompletableFuture.runAsync({
        val snapshots = YamlConfiguration.loadConfiguration(snapshotsFile); snapshots.set("snapshots.$snapshotId", encoded); snapshots.save(snapshotsFile)
        val yaml = YamlConfiguration.loadConfiguration(rewardsFile); val list = rewards(yaml, plan)
        list += base("item").apply { put("provider", "snapshot"); put("id", snapshotId); put("amount", amount.coerceAtLeast(1)) }
        yaml.set("${root(plan)}.rewards", list); yaml.save(rewardsFile)
    }, executor)
    fun update(plan: String, index: Int, key: String, value: Any?) = edit { yaml -> val list = rewards(yaml, plan); requireIndex(list, index); list[index][key] = value; yaml.set("${root(plan)}.rewards", list) }
    fun setTrigger(plan: String, index: Int, trigger: String) = edit { yaml ->
        val list = rewards(yaml, plan); requireIndex(list, index); list[index]["trigger"] = trigger
        if (trigger == "session-milestone" && list[index]["unlock-after"].toString().lowercase() in setOf("null", "0", "0s", "none")) list[index]["unlock-after"] = "30m"
        yaml.set("${root(plan)}.rewards", list)
    }
    fun remove(plan: String, index: Int) = edit { yaml -> val list = rewards(yaml, plan); requireIndex(list, index); list.removeAt(index); yaml.set("${root(plan)}.rewards", list) }

    private fun append(plan: String, reward: MutableMap<String, Any?>) = edit { yaml -> val list = rewards(yaml, plan); list += reward; yaml.set("${root(plan)}.rewards", list) }
    private fun edit(action: (YamlConfiguration) -> Unit): CompletableFuture<Void> = CompletableFuture.runAsync({ val yaml = YamlConfiguration.loadConfiguration(rewardsFile); action(yaml); yaml.save(rewardsFile) }, executor)
    private fun rewards(yaml: YamlConfiguration, plan: String): MutableList<MutableMap<String, Any?>> {
        require(yaml.contains(root(plan))) { Messages.raw("admin.error.plan-missing", mapOf("plan" to plan)) }
        return yaml.getMapList("${root(plan)}.rewards").map { raw -> raw.entries.associate { it.key.toString() to it.value }.toMutableMap() }.toMutableList()
    }
    private fun base(type: String) = linkedMapOf<String, Any?>("type" to type, "chance" to 100.0, "trigger" to "cycle", "unlock-after" to "0s")
    private fun requireIndex(list: List<*>, index: Int) { require(index in list.indices) { Messages.raw("admin.error.reward-index") } }
    private fun root(id: String) = "reward-plans.$id"
    private fun sectionMap(section: ConfigurationSection): Map<String, Any?> = section.getKeys(false).associateWith { key ->
        section.getConfigurationSection(key)?.let(::sectionMap) ?: section.get(key)
    }
    override fun close() { executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS) }
}
