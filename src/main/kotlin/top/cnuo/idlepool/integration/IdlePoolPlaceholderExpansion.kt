package top.cnuo.idlepool.integration

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import top.cnuo.idlepool.IdlePoolPlugin
import top.cnuo.idlepool.session.SessionManager
import top.cnuo.idlepool.storage.SqliteStore
import top.cnuo.idlepool.util.TimeFormats

class IdlePoolPlaceholderExpansion(
    private val plugin: IdlePoolPlugin,
    private val sessions: SessionManager,
    private val store: SqliteStore,
) : PlaceholderExpansion() {
    override fun getIdentifier() = "idlepool"
    override fun getAuthor() = "Chirnuo"
    override fun getVersion() = plugin.pluginMeta.version
    override fun persist() = true
    override fun canRegister() = true
    override fun onRequest(player: OfflinePlayer?, params: String): String {
        val id = player?.uniqueId ?: return ""
        val session = sessions.find(id).orElse(null)
        return when (params.lowercase()) {
            "active" -> (session != null).toString()
            "pool" -> session?.pool?.id.orEmpty()
            "session_time" -> TimeFormats.clock(session?.sessionSeconds ?: 0)
            "total_time" -> TimeFormats.clock(session?.totalSeconds ?: 0)
            "next_reward" -> TimeFormats.clock(session?.let { (it.pool.rewardCycle.seconds - it.progressSeconds).coerceAtLeast(0) } ?: 0)
            "earned" -> (session?.earned ?: 0).toString()
            "inbox_count" -> store.cachedInboxCount(id).toString()
            else -> ""
        }
    }
}
