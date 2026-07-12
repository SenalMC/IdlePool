package top.cnuo.idlepool.reward;

import java.time.Duration;
import java.util.Objects;

public record RewardDefinition(
        RewardType type,
        String provider,
        String itemId,
        int itemAmount,
        double moneyAmount,
        String command,
        double chance,
        RewardTrigger trigger,
        Duration unlockAfter
) {
    public RewardDefinition {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(unlockAfter, "unlockAfter");
        if (unlockAfter.isNegative()) {
            throw new IllegalArgumentException("Reward unlock time cannot be negative");
        }
        if (trigger == RewardTrigger.SESSION_MILESTONE && unlockAfter.isZero()) {
            throw new IllegalArgumentException("Session milestone reward requires a positive unlock time");
        }
    }

    public boolean eligibleForCycle(long sessionSeconds) {
        return trigger == RewardTrigger.CYCLE && sessionSeconds >= unlockAfter.toSeconds();
    }

    public boolean milestoneCrossed(long previousSessionSeconds, long sessionSeconds) {
        long threshold = unlockAfter.toSeconds();
        return trigger == RewardTrigger.SESSION_MILESTONE
                && previousSessionSeconds < threshold
                && sessionSeconds >= threshold;
    }
}
