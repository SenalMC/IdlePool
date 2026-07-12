package top.cnuo.idlepool.util

import java.time.Duration
import java.util.Locale

object DurationParser {
    private val part = Regex("(\\d+)([dhms])")

    @JvmStatic
    fun parse(input: String?): Duration {
        require(!input.isNullOrBlank()) { "Duration cannot be blank" }
        val normalized = input.lowercase(Locale.ROOT).replace(" ", "")
        var seconds = 0L
        var cursor = 0
        for (match in part.findAll(normalized)) {
            require(match.range.first == cursor) { "Invalid duration: $input" }
            val value = match.groupValues[1].toLong()
            seconds = Math.addExact(seconds, Math.multiplyExact(value, unitSeconds(match.groupValues[2][0])))
            cursor = match.range.last + 1
        }
        require(cursor == normalized.length && seconds > 0) { "Invalid duration: $input" }
        return Duration.ofSeconds(seconds)
    }

    private fun unitSeconds(unit: Char) = when (unit) {
        'd' -> 86_400L
        'h' -> 3_600L
        'm' -> 60L
        's' -> 1L
        else -> throw IllegalArgumentException("Unsupported duration unit: $unit")
    }
}
