package top.cnuo.idlepool.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml

class LanguageFilesTest {
    @Test fun `english and chinese expose matching keys`() {
        val chinese = mutableSetOf<String>(); val english = mutableSetOf<String>()
        flatten("", load("message.yml"), chinese); flatten("", load("en.yml"), english)
        assertEquals(chinese, english, "message.yml and en.yml must expose the same message keys")
    }
    private fun load(name: String): Map<String, Any?> {
        val stream = javaClass.classLoader.getResourceAsStream(name); assertNotNull(stream, "Missing language resource: $name")
        @Suppress("UNCHECKED_CAST") return Yaml().load(stream) as Map<String, Any?>
    }
    private fun flatten(prefix: String, section: Map<String, Any?>, keys: MutableSet<String>) {
        section.forEach { (name, value) ->
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            if (value is Map<*, *>) { @Suppress("UNCHECKED_CAST") flatten(path, value as Map<String, Any?>, keys) } else keys += path
        }
    }
}
