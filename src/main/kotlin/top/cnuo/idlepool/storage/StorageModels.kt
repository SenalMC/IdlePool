package top.cnuo.idlepool.storage

import java.time.Instant
import java.util.UUID

data class ProgressRecord(
    val progressSeconds: Long,
    val totalSeconds: Long,
    val expiresAtEpochSecond: Long,
    val rewardSequence: Long,
    val pityCount: Long = 0,
) {
    fun discardExpired(nowEpochSecond: Long): ProgressRecord =
        if ((progressSeconds > 0 || pityCount > 0) && expiresAtEpochSecond in 1..nowEpochSecond) copy(progressSeconds = 0, expiresAtEpochSecond = 0, pityCount = 0)
        else this

    fun progressSeconds() = progressSeconds
    fun totalSeconds() = totalSeconds
    fun expiresAtEpochSecond() = expiresAtEpochSecond
    fun rewardSequence() = rewardSequence
    fun pityCount() = pityCount

    companion object {
        @JvmStatic fun empty() = ProgressRecord(0, 0, 0, 0, 0)
    }
}

data class InboxEntry(
    val id: Long,
    val poolId: String,
    val provider: String,
    val itemId: String,
    val amount: Int,
    val displayName: String,
    val createdAt: Instant,
) {
    fun id() = id
    fun poolId() = poolId
    fun provider() = provider
    fun itemId() = itemId
    fun amount() = amount
    fun displayName() = displayName
    fun createdAt() = createdAt
}

data class InboxPage(val entries: List<InboxEntry>, val page: Int, val pages: Int, val totalEntries: Int)

data class ClaimReservation(val token: String, val entry: InboxEntry)

data class RewardLogEntry(
    val settlementId: String,
    val playerId: String,
    val poolId: String,
    val rewardType: String,
    val rewardKey: String,
    val amount: Double,
    val status: String,
    val detail: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RewardLogPage(val entries: List<RewardLogEntry>, val page: Int, val pages: Int, val totalEntries: Int)

data class ReviewEntry(
    val id: Long,
    val playerId: String,
    val poolId: String,
    val provider: String,
    val itemId: String,
    val amount: Int,
    val displayName: String,
    val claimToken: String,
    val detail: String,
    val createdAt: Instant,
)

data class ReviewPage(val entries: List<ReviewEntry>, val page: Int, val pages: Int, val totalEntries: Int)

enum class StatsPeriod { DAY, WEEK, MONTH, TOTAL;
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name.equals(value, true) } ?: TOTAL }
}

data class PlayerStats(
    val playerId: String,
    val playerName: String,
    val period: StatsPeriod,
    val seconds: Long,
    val cycles: Long,
    val rewards: Long,
    val longestSession: Long,
    val rank: Int,
)

data class LeaderboardEntry(
    val playerId: String,
    val playerName: String,
    val seconds: Long,
    val cycles: Long,
    val rewards: Long,
    val rank: Int,
)

data class SessionCheckpoint(
    val sessionId: String,
    val playerId: UUID,
    val playerName: String,
    val poolId: String,
    val seconds: Long,
    val cycles: Long,
    val rewards: Long,
)
