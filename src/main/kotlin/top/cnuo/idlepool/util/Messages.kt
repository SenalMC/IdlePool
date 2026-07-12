package top.cnuo.idlepool.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader

object Messages {
    private val miniMessage = MiniMessage.miniMessage()
    @Volatile private var language = YamlConfiguration()
    @Volatile private var activeFile = "message.yml"

    @JvmStatic
    fun load(plugin: JavaPlugin) {
        var selected = plugin.config.getString("language.file", "message.yml") ?: "message.yml"
        if (!selected.matches(Regex("[A-Za-z0-9_.-]+\\.yml"))) {
            plugin.logger.warning("语言文件名不安全，已回退到 message.yml。")
            selected = "message.yml"
        }
        var file = File(plugin.dataFolder, selected)
        if (!file.isFile) {
            plugin.logger.warning("语言文件不存在：$selected，已回退到 message.yml。")
            selected = "message.yml"
            file = File(plugin.dataFolder, selected)
        }
        val loaded = YamlConfiguration.loadConfiguration(file)
        val fallbackName = if (plugin.getResource(selected) != null) selected else "message.yml"
        plugin.getResource(fallbackName)?.use { stream ->
            val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8))
            loaded.setDefaults(defaults)
            val missing = defaults.getKeys(true).count { defaults.isString(it) || defaults.isList(it) }
                .minus(loaded.getKeys(true).count { loaded.contains(it, true) && (loaded.isString(it) || loaded.isList(it)) })
                .coerceAtLeast(0)
            if (missing > 0) plugin.logger.info("语言文件缺少 $missing 个键，已使用内置 $fallbackName 回退。")
        }
        language = loaded
        activeFile = selected
        plugin.logger.info("已加载语言文件 $activeFile。")
    }

    @JvmStatic fun languageFile() = activeFile
    @JvmStatic fun raw(key: String) = language.getString(key) ?: "<red>Missing message: $key</red>"
    @JvmStatic fun raw(key: String, placeholders: Map<String, String>) = replace(raw(key), placeholders)
    @JvmStatic fun get(key: String): Component = parse(raw(key))
    @JvmStatic fun get(key: String, placeholders: Map<String, String>) = miniMessage.deserialize(raw(key, placeholders))
    @JvmStatic fun list(key: String): List<Component> = list(key, emptyMap())

    @JvmStatic
    fun list(key: String, placeholders: Map<String, String>): List<Component> {
        val lines = language.getStringList(key)
        if (lines.isEmpty() && !language.isList(key)) return listOf(get(key, placeholders))
        return lines.map { miniMessage.deserialize(replace(it, placeholders)) }
    }

    @JvmStatic fun send(sender: CommandSender, key: String) = sender.sendMessage(get(key))
    @JvmStatic fun send(sender: CommandSender, key: String, placeholders: Map<String, String>) = sender.sendMessage(get(key, placeholders))
    @JvmStatic fun parse(input: String?): Component = miniMessage.deserialize(input.orEmpty())
    @JvmStatic fun parse(input: String?, placeholders: Map<String, String>) = miniMessage.deserialize(replace(input.orEmpty(), placeholders))

    private fun replace(input: String, placeholders: Map<String, String>) = placeholders.entries.fold(input) { text, (key, value) ->
        text.replace("{$key}", value.replace("<", "\\<"))
    }
}
