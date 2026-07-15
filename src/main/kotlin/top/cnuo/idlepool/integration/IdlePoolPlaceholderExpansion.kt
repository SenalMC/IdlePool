package top.cnuo.idlepool.integration

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import top.cnuo.idlepool.IdlePoolPlugin
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.storage.StatsPeriod
import top.cnuo.idlepool.activity.ActivityEventRepository
import top.cnuo.idlepool.util.TimeFormats

class IdlePoolPlaceholderExpansion(
    private val plugin: IdlePoolPlugin,
    private val sessions: SessionManager,
    private val store: SqliteStore,
    private val activities: ActivityEventRepository,
) : PlaceholderExpansion() {
    override fun getIdentifier() = "idlepool"
    override fun getAuthor() = "Chirnuo"
    override fun getVersion() = plugin.pluginMeta.version
    override fun persist() = true
    override fun canRegister() = true
    override fun onRequest(player: OfflinePlayer?, params: String): String {
        val id = player?.uniqueId ?: return ""
        val session = sessions.find(id).orElse(null)
        fun stats(period: StatsPeriod) = store.cachedStats(id, period).also { if (it == null) store.refreshStats(id) }
        return when (params.lowercase()) {
            "active" -> (session != null).toString()
            "pool" -> session?.pool?.id.orEmpty()
            "session_time" -> TimeFormats.clock(session?.sessionSeconds ?: 0)
            "total_time" -> TimeFormats.clock(session?.totalSeconds ?: stats(StatsPeriod.TOTAL)?.seconds ?: 0)
            "next_reward" -> TimeFormats.clock(session?.let { (it.pool.rewardCycle.seconds - it.progressSeconds).coerceAtLeast(0) } ?: 0)
            "earned" -> (session?.earned ?: 0).toString()
            "inbox_count" -> store.cachedInboxCount(id).toString()
            "daily_time" -> TimeFormats.clock(stats(StatsPeriod.DAY)?.seconds ?: 0)
            "weekly_time" -> TimeFormats.clock(stats(StatsPeriod.WEEK)?.seconds ?: 0)
            "monthly_time" -> TimeFormats.clock(stats(StatsPeriod.MONTH)?.seconds ?: 0)
            "longest_session" -> TimeFormats.clock(stats(StatsPeriod.TOTAL)?.longestSession ?: 0)
            "cycles" -> (stats(StatsPeriod.TOTAL)?.cycles ?: 0).toString()
            "rewards" -> (stats(StatsPeriod.TOTAL)?.rewards ?: 0).toString()
            "rank_daily" -> (stats(StatsPeriod.DAY)?.rank ?: 0).toString()
            "rank_weekly" -> (stats(StatsPeriod.WEEK)?.rank ?: 0).toString()
            "rank_monthly" -> (stats(StatsPeriod.MONTH)?.rank ?: 0).toString()
            "rank_total" -> (stats(StatsPeriod.TOTAL)?.rank ?: 0).toString()
            "event" -> session?.let { activities.multipliers(it.pool.id).label }.orEmpty()
            "progress_multiplier" -> session?.let { activities.multipliers(it.pool.id).progress.toString() } ?: "1.0"
            else -> ""
        }
    }
}
