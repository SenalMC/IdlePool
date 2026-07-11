package cn.guajichi.idlepool.session;

import cn.guajichi.idlepool.pool.PoolDefinition;
import cn.guajichi.idlepool.storage.ProgressRecord;

import java.util.UUID;

public final class ActiveSession {
    private final UUID playerId;
    private final PoolDefinition pool;
    private long sessionSeconds;
    private long progressSeconds;
    private long totalSeconds;
    private long rewardSequence;
    private long earned;

    public ActiveSession(UUID playerId, PoolDefinition pool, ProgressRecord progress) {
        this.playerId = playerId;
        this.pool = pool;
        this.progressSeconds = progress.progressSeconds();
        this.totalSeconds = progress.totalSeconds();
        this.rewardSequence = progress.rewardSequence();
    }

    public void tick() {
        sessionSeconds++;
        progressSeconds++;
        totalSeconds++;
    }

    public boolean cycleReady() {
        return progressSeconds >= pool.rewardCycle().toSeconds();
    }

    public void completeCycle(int granted) {
        progressSeconds -= pool.rewardCycle().toSeconds();
        rewardSequence++;
        earned += granted;
    }

    public void recordMilestone(int granted) {
        earned += granted;
    }

    public ProgressRecord snapshot(long expiresAtEpochSecond) {
        return new ProgressRecord(progressSeconds, totalSeconds, expiresAtEpochSecond, rewardSequence);
    }

    public UUID playerId() {
        return playerId;
    }

    public PoolDefinition pool() {
        return pool;
    }

    public long sessionSeconds() {
        return sessionSeconds;
    }

    public long progressSeconds() {
        return progressSeconds;
    }

    public long totalSeconds() {
        return totalSeconds;
    }

    public long earned() {
        return earned;
    }
}
