package cn.guajichi.idlepool.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

public final class Messages {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Messages() {
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(input == null ? "" : input);
    }

    public static Component parse(String input, Map<String, String> placeholders) {
        String rendered = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", escape(entry.getValue()));
        }
        return MINI_MESSAGE.deserialize(rendered);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("<", "\\<");
    }
}
