package top.cnuo.idlepool.session

import top.cnuo.idlepool.pool.PoolDefinition
import top.cnuo.idlepool.storage.ProgressRecord
import java.util.UUID

class ActiveSession(val playerId: UUID, val pool: PoolDefinition, progress: ProgressRecord) {
    val sessionId: String = UUID.randomUUID().toString()
    var sessionSeconds = 0L; private set
    var progressSeconds = progress.progressSeconds; private set
    var totalSeconds = progress.totalSeconds; private set
    var rewardSequence = progress.rewardSequence; private set
    var pityCount = progress.pityCount; private set
    var earned = 0L; private set
    var completedCycles = 0L; private set
    private var progressCarry = 0.0

    fun tick(progressMultiplier: Double = 1.0) {
        sessionSeconds++
        totalSeconds++
        progressCarry += progressMultiplier.coerceIn(0.0, 100.0)
        val gained = progressCarry.toLong()
        progressCarry -= gained
        progressSeconds += gained
    }
    fun cycleReady() = progressSeconds >= pool.rewardCycle.seconds
    fun completeCycle(granted: Int, newPityCount: Long = pityCount) {
        progressSeconds -= pool.rewardCycle.seconds
        rewardSequence++
        completedCycles++
        earned += granted
        pityCount = newPityCount
    }
    fun recordMilestone(granted: Int) { earned += granted }
    fun snapshot(expiresAt: Long) = ProgressRecord(progressSeconds, totalSeconds, expiresAt, rewardSequence, pityCount)
    fun playerId() = playerId
    fun pool() = pool
    fun sessionSeconds() = sessionSeconds
    fun progressSeconds() = progressSeconds
    fun totalSeconds() = totalSeconds
    fun earned() = earned
    fun rewardSequence() = rewardSequence
    fun sessionId() = sessionId
}
