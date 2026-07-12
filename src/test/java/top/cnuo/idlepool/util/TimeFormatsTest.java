package top.cnuo.idlepool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatsTest {
    @Test
    void formatsClockWithoutWrappingHours() {
        assertEquals("00:00:00", TimeFormats.clock(-1));
        assertEquals("01:01:01", TimeFormats.clock(3_661));
        assertEquals("25:00:00", TimeFormats.clock(90_000));
    }
}
