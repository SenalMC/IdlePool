package top.cnuo.idlepool.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ConfigMigrator(private val plugin: JavaPlugin) {
    fun migrate() {
        migrateConfig()
        migrateDataFile("pools.yml")
        migrateDataFile("rewards.yml")
    }

    private fun migrateConfig() {
        val file = File(plugin.dataFolder, "config.yml")
        val yaml = YamlConfiguration.loadConfiguration(file)
        val version = yaml.getInt("config-version", 1)
        if (version >= CURRENT) return
        backup(file, version)
        yaml.set("config-version", CURRENT)
        if (!yaml.contains("language.file")) yaml.set("language.file", "message.yml")
        if (!yaml.contains("inbox.open-on-stop")) yaml.set("inbox.open-on-stop", true)
        if (!yaml.contains("inbox.page-size")) yaml.set("inbox.page-size", 45)
        if (!yaml.contains("storage.reward-log-retention-days")) yaml.set("storage.reward-log-retention-days", 30)
        yaml.save(file)
        plugin.logger.info("已将 config.yml 从 schema $version 自动迁移到 $CURRENT。")
    }

    private fun migrateDataFile(name: String) {
        val file = File(plugin.dataFolder, name)
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        val version = yaml.getInt("schema-version", 1)
        if (version >= CURRENT) return
        backup(file, version)
        yaml.set("schema-version", CURRENT)
        yaml.save(file)
        plugin.logger.info("已将 $name 从 schema $version 自动迁移到 $CURRENT。")
    }

    private fun backup(file: File, version: Int) {
        val backup = File(file.parentFile, "${file.name}.v$version.bak")
        if (!backup.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES)
    }

    companion object { const val CURRENT = 2 }
}
