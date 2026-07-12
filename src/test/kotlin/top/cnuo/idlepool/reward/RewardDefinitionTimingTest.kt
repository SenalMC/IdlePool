package top.cnuo.idlepool.reward

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

class RewardDefinitionTimingTest {
    @Test fun `cycle unlock and milestone crossing are distinct`() {
        val cycle = reward(RewardTrigger.CYCLE, Duration.ofMinutes(30))
        assertFalse(cycle.eligibleForCycle(1799)); assertTrue(cycle.eligibleForCycle(1800)); assertFalse(cycle.milestoneCrossed(1799, 1800))
        val milestone = reward(RewardTrigger.SESSION_MILESTONE, Duration.ofHours(1))
        assertTrue(milestone.milestoneCrossed(3599, 3600)); assertFalse(milestone.milestoneCrossed(3600, 3601)); assertFalse(milestone.eligibleForCycle(7200))
    }
    @Test fun `milestone requires positive time`() { assertThrows(IllegalArgumentException::class.java) { reward(RewardTrigger.SESSION_MILESTONE, Duration.ZERO) } }
    private fun reward(trigger: RewardTrigger, time: Duration) = RewardDefinition(RewardType.ITEM, "vanilla", "DIAMOND", 1, 0.0, "", 100.0, trigger, time)
}
