package top.cnuo.idlepool.storage

import java.time.Instant

data class ProgressRecord(
    val progressSeconds: Long,
    val totalSeconds: Long,
    val expiresAtEpochSecond: Long,
    val rewardSequence: Long,
) {
    fun discardExpired(nowEpochSecond: Long): ProgressRecord =
        if (progressSeconds > 0 && expiresAtEpochSecond in 1..nowEpochSecond) copy(progressSeconds = 0, expiresAtEpochSecond = 0)
        else this

    fun progressSeconds() = progressSeconds
    fun totalSeconds() = totalSeconds
    fun expiresAtEpochSecond() = expiresAtEpochSecond
    fun rewardSequence() = rewardSequence

    companion object {
        @JvmStatic fun empty() = ProgressRecord(0, 0, 0, 0)
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
    val createdAt: Instant,
)
