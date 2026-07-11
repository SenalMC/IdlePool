package cn.guajichi.idlepool.storage;

public record ProgressRecord(
        long progressSeconds,
        long totalSeconds,
        long expiresAtEpochSecond,
        long rewardSequence
) {
    public static ProgressRecord empty() {
        return new ProgressRecord(0, 0, 0, 0);
    }

    public ProgressRecord discardExpired(long nowEpochSecond) {
        if (progressSeconds > 0 && expiresAtEpochSecond > 0 && expiresAtEpochSecond <= nowEpochSecond) {
            return new ProgressRecord(0, totalSeconds, 0, rewardSequence);
        }
        return this;
    }
}
