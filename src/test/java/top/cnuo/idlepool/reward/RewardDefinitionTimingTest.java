package top.cnuo.idlepool.reward;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardDefinitionTimingTest {
    @Test
    void cycleRewardUnlocksOnlyAfterContinuousSessionThreshold() {
        RewardDefinition reward = reward(RewardTrigger.CYCLE, Duration.ofMinutes(30));

        assertFalse(reward.eligibleForCycle(Duration.ofMinutes(29).toSeconds()));
        assertTrue(reward.eligibleForCycle(Duration.ofMinutes(30).toSeconds()));
        assertFalse(reward.milestoneCrossed(1_799, 1_800));
    }

    @Test
    void milestoneTriggersOnlyWhenThresholdIsCrossed() {
        RewardDefinition reward = reward(RewardTrigger.SESSION_MILESTONE, Duration.ofHours(1));

        assertFalse(reward.milestoneCrossed(3_598, 3_599));
        assertTrue(reward.milestoneCrossed(3_599, 3_600));
        assertFalse(reward.milestoneCrossed(3_600, 3_601));
        assertFalse(reward.eligibleForCycle(7_200));
    }

    @Test
    void milestoneRequiresPositiveTime() {
        assertThrows(IllegalArgumentException.class,
                () -> reward(RewardTrigger.SESSION_MILESTONE, Duration.ZERO));
    }

    @Test
    void triggerParserAcceptsConfigurationAliases() {
        assertTrue(RewardTrigger.parse("cycle") == RewardTrigger.CYCLE);
        assertTrue(RewardTrigger.parse("session-milestone") == RewardTrigger.SESSION_MILESTONE);
        assertTrue(RewardTrigger.parse("milestone") == RewardTrigger.SESSION_MILESTONE);
    }

    private static RewardDefinition reward(RewardTrigger trigger, Duration unlockAfter) {
        return new RewardDefinition(
                RewardType.ITEM, "vanilla", "DIAMOND", 1, 0, "", 100,
                trigger, unlockAfter
        );
    }
}
