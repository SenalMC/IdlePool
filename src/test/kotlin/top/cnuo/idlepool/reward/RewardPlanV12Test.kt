package top.cnuo.idlepool.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RewardPlanV12Test {
    @Test fun `selection modes accept configuration names`() {
        assertEquals(SelectionMode.INDEPENDENT, SelectionMode.parse("independent"))
        assertEquals(SelectionMode.WEIGHTED_ONE, SelectionMode.parse("weighted-one"))
        assertEquals(SelectionMode.WEIGHTED_MULTIPLE, SelectionMode.parse("weighted_multiple"))
    }
    @Test fun `enabled pity requires a positive cycle count`() {
        assertThrows(IllegalArgumentException::class.java) { PityConfig(true, 0, 0, true) }
    }
    @Test fun `pity counts only eligible draws and always resets after forced grant`() {
        val pity = PityConfig(true, 20, 2, false)
        assertEquals(7, pity.nextCount(7, targetEligible = false, targetWon = false, pityTriggered = false))
        assertEquals(8, pity.nextCount(7, targetEligible = true, targetWon = true, pityTriggered = false))
        assertEquals(0, pity.nextCount(19, targetEligible = true, targetWon = true, pityTriggered = true))
    }
}
