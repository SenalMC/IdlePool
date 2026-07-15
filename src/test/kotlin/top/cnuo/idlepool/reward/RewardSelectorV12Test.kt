package top.cnuo.idlepool.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RewardSelectorV12Test {
    @Test
    fun `weighted multiple draw is deterministic unique and ignores zero weights`() {
        val candidates = listOf(reward("a", 10.0), reward("b", 20.0), reward("c", 0.0), reward("d", 70.0)).withIndex().toList()
        val first = DeterministicRewardSelector.weightedDraw(candidates, 3, "stable-seed")
        val second = DeterministicRewardSelector.weightedDraw(candidates, 3, "stable-seed")

        assertEquals(first.map { it.index }, second.map { it.index })
        assertEquals(3, first.size)
        assertEquals(first.size, first.map { it.index }.distinct().size)
        assertTrue(first.none { it.value.itemId == "c" })
    }

    @Test fun `stable roll remains inside percentage range`() {
        val roll = DeterministicRewardSelector.roll("idlepool")
        assertTrue(roll >= 0.0 && roll < 100.0)
    }

    private fun reward(id: String, weight: Double) = RewardDefinition(
        RewardType.ITEM, "vanilla", id, 1, 0.0, "", 100.0, weight, RewardTrigger.CYCLE, Duration.ZERO,
    )
}
