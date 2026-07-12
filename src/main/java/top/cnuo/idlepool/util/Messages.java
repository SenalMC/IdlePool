package top.cnuo.idlepool.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class Messages {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static volatile YamlConfiguration language = new YamlConfiguration();
    private static volatile String languageFile = "message.yml";

    private Messages() {
    }

    public static void load(JavaPlugin plugin) {
        String selected = plugin.getConfig().getString("language.file", "message.yml");
        if (selected == null || !selected.matches("[A-Za-z0-9_.-]+\\.yml")) {
            plugin.getLogger().warning("语言文件名不安全，已回退到 message.yml。");
            selected = "message.yml";
        }
        File file = new File(plugin.getDataFolder(), selected);
        if (!file.isFile()) {
            plugin.getLogger().warning("语言文件不存在：" + selected + "，已回退到 message.yml。");
            selected = "message.yml";
            file = new File(plugin.getDataFolder(), selected);
        }
        language = YamlConfiguration.loadConfiguration(file);
        languageFile = selected;
        plugin.getLogger().info("已加载语言文件 " + languageFile + "。");
    }

    public static String languageFile() {
        return languageFile;
    }

    public static String raw(String key) {
        return language.getString(key, "<red>Missing message: " + key + "</red>");
    }

    public static String raw(String key, Map<String, String> placeholders) {
        return replace(raw(key), placeholders);
    }

    public static Component get(String key) {
        return parse(raw(key));
    }

    public static Component get(String key, Map<String, String> placeholders) {
        return MINI_MESSAGE.deserialize(raw(key, placeholders));
    }

    public static List<Component> list(String key) {
        return list(key, Map.of());
    }

    public static List<Component> list(String key, Map<String, String> placeholders) {
        List<String> lines = language.getStringList(key);
        if (lines.isEmpty() && !language.isList(key)) {
            return List.of(get(key, placeholders));
        }
        return lines.stream()
                .map(line -> MINI_MESSAGE.deserialize(replace(line, placeholders)))
                .toList();
    }

    public static void send(CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public static void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(input == null ? "" : input);
    }

    public static Component parse(String input, Map<String, String> placeholders) {
        return MINI_MESSAGE.deserialize(replace(input, placeholders));
    }

    private static String replace(String input, Map<String, String> placeholders) {
        String rendered = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        }
        return rendered;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("<", "\\<");
    }
}
