package cn.guajichi.idlepool.reward;

import java.util.List;

public record RewardPlan(String id, List<RewardDefinition> rewards) {
}
