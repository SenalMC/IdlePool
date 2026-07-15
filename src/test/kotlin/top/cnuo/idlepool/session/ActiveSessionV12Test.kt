package top.cnuo.idlepool.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.cnuo.idlepool.pool.CuboidRegion
import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.pool.PoolVisuals
import top.cnuo.idlepool.storage.ProgressRecord
import java.time.Duration
import java.util.UUID

class ActiveSessionV12Test {
    @Test fun `fractional event multiplier advances reward progress without inflating play time`() {
        val session = ActiveSession(UUID.randomUUID(), pool(), ProgressRecord.empty())
        session.tick(1.5); session.tick(1.5)
        assertEquals(2, session.sessionSeconds)
        assertEquals(2, session.totalSeconds)
        assertEquals(3, session.progressSeconds)
    }

    @Test fun `cycle completion records pity and counters`() {
        val session = ActiveSession(UUID.randomUUID(), pool(), ProgressRecord(10, 10, 0, 4, 8))
        assertTrue(session.cycleReady())
        session.completeCycle(3, 0)
        assertEquals(1, session.completedCycles)
        assertEquals(3, session.earned)
        assertEquals(5, session.rewardSequence)
        assertEquals(0, session.pityCount)
    }

    private fun pool() = PoolDefinition(
        "test", true, "test", CuboidRegion("world", 0,0,0,1,1,1), "", 0,
        Duration.ofSeconds(10), Duration.ofDays(7), "basic", PoolVisuals("", "", ""),
    )
}
