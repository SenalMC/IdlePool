package top.cnuo.idlepool.api

import top.cnuo.idlepool.activity.ActivityEventRepository
import top.cnuo.idlepool.session.SessionManager
import java.util.Optional
import java.util.UUID

data class IdlePoolSessionView(
    val playerId: UUID,
    val poolId: String,
    val sessionSeconds: Long,
    val progressSeconds: Long,
    val totalSeconds: Long,
    val earned: Long,
    val pityCount: Long,
)

interface IdlePoolApi {
    fun isActive(playerId: UUID): Boolean
    fun session(playerId: UUID): Optional<IdlePoolSessionView>
    fun activePlayers(poolId: String): Int
    fun activeEvents(poolId: String): List<String>
}

class IdlePoolApiImpl(private val sessions: SessionManager, private val activities: ActivityEventRepository) : IdlePoolApi {
    override fun isActive(playerId: UUID) = sessions.isActive(playerId)
    override fun session(playerId: UUID): Optional<IdlePoolSessionView> = sessions.find(playerId).map {
        IdlePoolSessionView(it.playerId, it.pool.id, it.sessionSeconds, it.progressSeconds, it.totalSeconds, it.earned, it.pityCount)
    }
    override fun activePlayers(poolId: String) = sessions.activeCount(poolId)
    override fun activeEvents(poolId: String) = activities.active(poolId).map { it.id }
}
