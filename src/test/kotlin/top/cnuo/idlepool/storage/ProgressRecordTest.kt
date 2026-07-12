package top.cnuo.idlepool.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressRecordTest {
    @Test fun `expiry clears only partial progress`() {
        val expired = ProgressRecord(120, 10000, 500, 8).discardExpired(500)
        assertEquals(0, expired.progressSeconds); assertEquals(10000, expired.totalSeconds); assertEquals(8, expired.rewardSequence)
    }
    @Test fun `unexpired progress is retained`() { val value = ProgressRecord(120, 10000, 501, 8); assertEquals(value, value.discardExpired(500)) }
}
