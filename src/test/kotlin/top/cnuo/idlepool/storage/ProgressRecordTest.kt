package top.cnuo.idlepool.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressRecordTest {
    @Test fun `expiry clears partial progress and pity but preserves totals and sequence`() {
        val expired = ProgressRecord(120, 10000, 500, 8, 6).discardExpired(500)
        assertEquals(0, expired.progressSeconds); assertEquals(0, expired.pityCount)
        assertEquals(10000, expired.totalSeconds); assertEquals(8, expired.rewardSequence)
        assertEquals(0, ProgressRecord(0, 10000, 500, 8, 6).discardExpired(500).pityCount)
    }
    @Test fun `unexpired progress is retained`() { val value = ProgressRecord(120, 10000, 501, 8); assertEquals(value, value.discardExpired(500)) }
}
