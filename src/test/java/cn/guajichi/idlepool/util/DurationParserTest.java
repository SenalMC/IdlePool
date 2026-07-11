package cn.guajichi.idlepool.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
    @Test
    void parsesSingleAndCompoundDurations() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d"));
        assertEquals(Duration.ofSeconds(5_430), DurationParser.parse("1h30m30s"));
        assertEquals(Duration.ofMinutes(10), DurationParser.parse("10m"));
    }

    @Test
    void rejectsMalformedDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("7days"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0s"));
    }
}
