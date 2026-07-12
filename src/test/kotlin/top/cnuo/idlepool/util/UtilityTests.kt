package top.cnuo.idlepool.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

class UtilityTests {
    @Test fun `parses durations`() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d"))
        assertEquals(Duration.ofSeconds(5430), DurationParser.parse("1h30m30s"))
        assertThrows(IllegalArgumentException::class.java) { DurationParser.parse("7days") }
        assertThrows(IllegalArgumentException::class.java) { DurationParser.parse("0s") }
    }
    @Test fun `formats clocks without wrapping hours`() {
        assertEquals("00:00:00", TimeFormats.clock(-1)); assertEquals("01:01:01", TimeFormats.clock(3661)); assertEquals("25:00:00", TimeFormats.clock(90000))
    }
}
