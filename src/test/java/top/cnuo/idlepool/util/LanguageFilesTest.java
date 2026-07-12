package top.cnuo.idlepool.util;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LanguageFilesTest {
    @Test
    void englishAndChineseLanguageFilesHaveMatchingKeys() {
        Map<String, Object> chinese = load("message.yml");
        Map<String, Object> english = load("en.yml");

        Set<String> chineseKeys = new LinkedHashSet<>();
        Set<String> englishKeys = new LinkedHashSet<>();
        flatten("", chinese, chineseKeys);
        flatten("", english, englishKeys);

        assertEquals(chineseKeys, englishKeys, "message.yml and en.yml must expose the same message keys");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String name) {
        InputStream stream = LanguageFilesTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, "Missing language resource: " + name);
        return new Yaml().load(stream);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> section, Set<String> keys) {
        section.forEach((name, value) -> {
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            if (value instanceof Map<?, ?> nested) {
                flatten(path, (Map<String, Object>) nested, keys);
            } else {
                keys.add(path);
            }
        });
    }
}
