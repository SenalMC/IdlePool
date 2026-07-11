package cn.guajichi.idlepool.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressRecordTest {
    @Test
    void expiresOnlyPartialCycleProgress() {
        ProgressRecord expired = new ProgressRecord(120, 10_000, 500, 8).discardExpired(500);
        assertEquals(0, expired.progressSeconds());
        assertEquals(10_000, expired.totalSeconds());
        assertEquals(8, expired.rewardSequence());
    }

    @Test
    void keepsUnexpiredProgress() {
        ProgressRecord current = new ProgressRecord(120, 10_000, 501, 8);
        assertEquals(current, current.discardExpired(500));
    }
}
