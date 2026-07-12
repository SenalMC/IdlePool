package top.cnuo.idlepool.reward;

import java.util.Locale;

public enum RewardTrigger {
    CYCLE,
    SESSION_MILESTONE;

    public static RewardTrigger parse(String input) {
        String normalized = input == null ? "cycle" : input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "cycle" -> CYCLE;
            case "milestone", "session-milestone", "session_milestone" -> SESSION_MILESTONE;
            default -> throw new IllegalArgumentException("Unsupported reward trigger: " + input);
        };
    }
}
