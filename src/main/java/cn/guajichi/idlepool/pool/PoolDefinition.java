package cn.guajichi.idlepool.pool;

import java.time.Duration;

public record PoolDefinition(
        String id,
        boolean enabled,
        String displayName,
        CuboidRegion region,
        String permission,
        int maxActivePlayers,
        Duration rewardCycle,
        Duration progressRetention,
        String rewardPlan,
        PoolVisuals visuals
) {
}
