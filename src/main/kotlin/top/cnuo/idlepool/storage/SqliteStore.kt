package top.cnuo.idlepool.storage

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

class SqliteStore private constructor(
    private val retentionDays: () -> Int,
    private val statisticsTimeZone: () -> String,
    private val logger: Logger,
    databaseFile: File,
) : AutoCloseable {
    constructor(plugin: JavaPlugin, databaseFile: File) : this(
        retentionDays = { plugin.config.getInt("storage.reward-log-retention-days", 90).coerceAtLeast(1) },
        statisticsTimeZone = { plugin.config.getString("statistics.time-zone", ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id },
        logger = plugin.logger,
        databaseFile = databaseFile,
    )

    internal constructor(
        databaseFile: File,
        retentionDays: Int = 90,
        statisticsTimeZone: String = "UTC",
        logger: Logger = Logger.getLogger("IdlePool-Test"),
    ) : this({ retentionDays.coerceAtLeast(1) }, { statisticsTimeZone }, logger, databaseFile)

    private val jdbcUrl: String
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "IdlePool-Storage").apply { isDaemon = true }
    }
    private val inboxCounts = ConcurrentHashMap<UUID, Int>()
    private val statsCache = ConcurrentHashMap<Pair<UUID, StatsPeriod>, PlayerStats>()

    init {
        databaseFile.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create database directory: $it" } }
        jdbcUrl = "jdbc:sqlite:${databaseFile.absolutePath}"
    }

    fun initialize() {
        try {
            Class.forName("org.sqlite.JDBC")
            open().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA journal_mode=WAL")
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_pool_progress (
                            player_uuid TEXT NOT NULL, pool_id TEXT NOT NULL,
                            progress_seconds INTEGER NOT NULL, total_seconds INTEGER NOT NULL,
                            expires_at INTEGER NOT NULL, reward_sequence INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL, PRIMARY KEY (player_uuid, pool_id)
                        )
                    """.trimIndent())
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS reward_inbox (
                            id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL,
                            pool_id TEXT NOT NULL, provider TEXT NOT NULL, item_id TEXT NOT NULL,
                            amount INTEGER NOT NULL, display_name TEXT NOT NULL, created_at INTEGER NOT NULL,
                            status TEXT NOT NULL DEFAULT 'PENDING', claim_token TEXT, claim_started_at INTEGER
                        )
                    """.trimIndent())
                    addColumn(connection, "reward_inbox", "claim_token", "TEXT")
                    addColumn(connection, "reward_inbox", "claim_started_at", "INTEGER")
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_reward_inbox_player ON reward_inbox(player_uuid, status, id)")
                    statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_reward_inbox_claim_token ON reward_inbox(claim_token) WHERE claim_token IS NOT NULL")
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS reward_ledger (
                            settlement_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, pool_id TEXT NOT NULL,
                            reward_type TEXT NOT NULL, reward_key TEXT NOT NULL, amount REAL NOT NULL,
                            status TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '',
                            created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_reward_ledger_player ON reward_ledger(player_uuid, created_at DESC)")
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS inbox_claim_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT, claim_token TEXT NOT NULL,
                            inbox_id INTEGER NOT NULL, player_uuid TEXT NOT NULL, claimed_amount INTEGER NOT NULL,
                            status TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    statement.execute("CREATE TABLE IF NOT EXISTS idlepool_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT NOT NULL)")
                    addColumn(connection, "player_pool_progress", "pity_count", "INTEGER NOT NULL DEFAULT 0")
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS session_stats_checkpoint (
                            session_id TEXT PRIMARY KEY, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL,
                            pool_id TEXT NOT NULL, seconds INTEGER NOT NULL, cycles INTEGER NOT NULL,
                            rewards INTEGER NOT NULL, updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                            player_uuid TEXT NOT NULL, pool_id TEXT NOT NULL, player_name TEXT NOT NULL,
                            total_seconds INTEGER NOT NULL DEFAULT 0, cycles INTEGER NOT NULL DEFAULT 0,
                            rewards INTEGER NOT NULL DEFAULT 0, longest_session INTEGER NOT NULL DEFAULT 0,
                            updated_at INTEGER NOT NULL, PRIMARY KEY(player_uuid,pool_id)
                        )
                    """.trimIndent())
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_period_stats (
                            period_type TEXT NOT NULL, period_key TEXT NOT NULL, player_uuid TEXT NOT NULL,
                            pool_id TEXT NOT NULL, player_name TEXT NOT NULL, seconds INTEGER NOT NULL DEFAULT 0,
                            cycles INTEGER NOT NULL DEFAULT 0, rewards INTEGER NOT NULL DEFAULT 0,
                            updated_at INTEGER NOT NULL,
                            PRIMARY KEY(period_type,period_key,player_uuid,pool_id)
                        )
                    """.trimIndent())
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_period_stats_rank ON player_period_stats(period_type,period_key,seconds DESC)")
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS admin_audit_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT, admin_name TEXT NOT NULL, action TEXT NOT NULL,
                            target_type TEXT NOT NULL, target_id TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '',
                            created_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_time ON admin_audit_log(created_at DESC)")
                    statement.execute("""
                        INSERT OR IGNORE INTO player_stats(player_uuid,pool_id,player_name,total_seconds,cycles,rewards,longest_session,updated_at)
                        SELECT player_uuid,pool_id,substr(player_uuid,1,8),total_seconds,0,0,0,updated_at FROM player_pool_progress
                    """.trimIndent())
                    statement.execute("INSERT INTO idlepool_meta(meta_key, meta_value) VALUES ('schema_version', '3') ON CONFLICT(meta_key) DO UPDATE SET meta_value='3'")
                    // Unknown outcome after a crash is never returned to PENDING automatically: this prevents duplication.
                    statement.execute("UPDATE reward_inbox SET status='REVIEW' WHERE status='CLAIMING'")
                    val cutoff = now() - TimeUnit.DAYS.toSeconds(retentionDays().toLong())
                    connection.prepareStatement("DELETE FROM reward_ledger WHERE created_at < ? AND status IN ('SUCCESS','FAILED')").use {
                        it.setLong(1, cutoff)
                        it.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM inbox_claim_log WHERE created_at < ?").use {
                        it.setLong(1, cutoff)
                        it.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM session_stats_checkpoint WHERE updated_at < ?").use {
                        it.setLong(1, now() - TimeUnit.DAYS.toSeconds(2)); it.executeUpdate()
                    }
                }
            }
        } catch (exception: Exception) {
            throw IllegalStateException("Cannot initialize SQLite", exception)
        }
    }

    fun loadProgress(playerId: UUID, poolId: String): CompletableFuture<ProgressRecord> = supplyAsync {
        open().use { connection -> connection.prepareStatement(
            "SELECT progress_seconds,total_seconds,expires_at,reward_sequence,pity_count FROM player_pool_progress WHERE player_uuid=? AND pool_id=?"
        ).use { statement ->
            statement.setString(1, playerId.toString()); statement.setString(2, poolId)
            statement.executeQuery().use { result ->
                if (!result.next()) ProgressRecord.empty() else ProgressRecord(
                    result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4), result.getLong(5)
                )
            }
        } }
    }

    fun saveProgress(playerId: UUID, poolId: String, progress: ProgressRecord): CompletableFuture<Void> = runAsync {
        open().use { connection -> connection.prepareStatement("""
            INSERT INTO player_pool_progress(player_uuid,pool_id,progress_seconds,total_seconds,expires_at,reward_sequence,pity_count,updated_at)
            VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(player_uuid,pool_id) DO UPDATE SET
            progress_seconds=excluded.progress_seconds,total_seconds=excluded.total_seconds,
            expires_at=excluded.expires_at,reward_sequence=excluded.reward_sequence,pity_count=excluded.pity_count,updated_at=excluded.updated_at
        """.trimIndent()).use { statement ->
            statement.setString(1, playerId.toString()); statement.setString(2, poolId)
            statement.setLong(3, progress.progressSeconds); statement.setLong(4, progress.totalSeconds)
            statement.setLong(5, progress.expiresAtEpochSecond); statement.setLong(6, progress.rewardSequence)
            statement.setLong(7, progress.pityCount); statement.setLong(8, now()); statement.executeUpdate()
        } }
    }

    fun enqueueItem(playerId: UUID, poolId: String, provider: String, itemId: String, amount: Int, displayName: String): CompletableFuture<Long> = supplyAsync {
        open().use { connection -> insertInbox(connection, playerId, poolId, provider, itemId, amount, displayName).also { refreshCount(connection, playerId) } }
    }

    fun enqueueItemSettlement(
        settlementId: String, playerId: UUID, poolId: String, provider: String,
        itemId: String, amount: Int, displayName: String,
    ): CompletableFuture<Boolean> = supplyAsync {
        transaction { connection ->
            if (!insertLedger(connection, settlementId, playerId, poolId, "ITEM", "$provider:$itemId", amount.toDouble(), "PROCESSING")) return@transaction false
            insertInbox(connection, playerId, poolId, provider, itemId, amount, displayName)
            updateLedger(connection, settlementId, "SUCCESS", "queued")
            refreshCount(connection, playerId)
            true
        }
    }

    fun reserveSettlement(
        settlementId: String, playerId: UUID, poolId: String, rewardType: String, rewardKey: String, amount: Double,
    ): CompletableFuture<Boolean> = supplyAsync {
        open().use { insertLedger(it, settlementId, playerId, poolId, rewardType, rewardKey, amount, "PROCESSING") }
    }

    fun completeSettlement(settlementId: String, success: Boolean, detail: String): CompletableFuture<Void> = runAsync {
        open().use { updateLedger(it, settlementId, if (success) "SUCCESS" else "FAILED", detail.take(512)) }
    }

    fun listInbox(playerId: UUID, limit: Int): CompletableFuture<List<InboxEntry>> =
        listInboxPage(playerId, 0, limit).thenApply(InboxPage::entries)

    fun listInboxPage(playerId: UUID, page: Int, pageSize: Int): CompletableFuture<InboxPage> = supplyAsync {
        open().use { connection ->
            val safeSize = pageSize.coerceIn(1, 45)
            val total = countPending(connection, playerId)
            val pages = maxOf(1, (total + safeSize - 1) / safeSize)
            val safePage = page.coerceIn(0, pages - 1)
            val entries = selectInbox(connection, playerId, safeSize, safePage * safeSize)
            inboxCounts[playerId] = total
            InboxPage(entries, safePage, pages, total)
        }
    }

    fun listAllInbox(playerId: UUID, limit: Int = 500): CompletableFuture<List<InboxEntry>> = supplyAsync {
        open().use { selectInbox(it, playerId, limit.coerceIn(1, 5000), 0) }
    }

    fun reserveInbox(playerId: UUID, entryId: Long): CompletableFuture<ClaimReservation?> = supplyAsync {
        transaction { connection ->
            val entry = selectEntry(connection, playerId, entryId) ?: return@transaction null
            val token = UUID.randomUUID().toString()
            connection.prepareStatement("UPDATE reward_inbox SET status='CLAIMING',claim_token=?,claim_started_at=? WHERE id=? AND player_uuid=? AND status='PENDING'").use { statement ->
                statement.setString(1, token); statement.setLong(2, now()); statement.setLong(3, entryId); statement.setString(4, playerId.toString())
                if (statement.executeUpdate() != 1) return@transaction null
            }
            ClaimReservation(token, entry)
        }
    }

    fun finishClaim(playerId: UUID, token: String, remaining: Int, claimed: Int): CompletableFuture<Boolean> = supplyAsync {
        transaction { connection ->
            val inboxId = connection.prepareStatement("SELECT id FROM reward_inbox WHERE player_uuid=? AND claim_token=? AND status='CLAIMING'").use { statement ->
                statement.setString(1, playerId.toString()); statement.setString(2, token)
                statement.executeQuery().use { if (it.next()) it.getLong(1) else return@transaction false }
            }
            val sql = if (remaining <= 0) "DELETE FROM reward_inbox WHERE id=? AND player_uuid=? AND claim_token=? AND status='CLAIMING'"
                else "UPDATE reward_inbox SET amount=?,status='PENDING',claim_token=NULL,claim_started_at=NULL WHERE id=? AND player_uuid=? AND claim_token=? AND status='CLAIMING'"
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                if (remaining > 0) statement.setInt(index++, remaining)
                statement.setLong(index++, inboxId); statement.setString(index++, playerId.toString()); statement.setString(index, token)
                if (statement.executeUpdate() != 1) return@transaction false
            }
            insertClaimLog(connection, token, inboxId, playerId, claimed, "SUCCESS", "")
            refreshCount(connection, playerId)
            true
        }
    }

    fun releaseClaim(playerId: UUID, token: String, detail: String): CompletableFuture<Void> = runAsync {
        transaction { connection ->
            val id = claimInboxId(connection, playerId, token) ?: return@transaction
            connection.prepareStatement("UPDATE reward_inbox SET status='PENDING',claim_token=NULL,claim_started_at=NULL WHERE id=? AND player_uuid=? AND claim_token=? AND status='CLAIMING'").use {
                it.setLong(1, id); it.setString(2, playerId.toString()); it.setString(3, token); it.executeUpdate()
            }
            insertClaimLog(connection, token, id, playerId, 0, "RELEASED", detail)
        }
    }

    fun markClaimReview(playerId: UUID, token: String, claimed: Int, detail: String): CompletableFuture<Void> = runAsync {
        transaction { connection ->
            val id = claimInboxId(connection, playerId, token) ?: return@transaction
            connection.prepareStatement("UPDATE reward_inbox SET status='REVIEW' WHERE id=? AND player_uuid=? AND claim_token=? AND status='CLAIMING'").use {
                it.setLong(1, id); it.setString(2, playerId.toString()); it.setString(3, token); it.executeUpdate()
            }
            insertClaimLog(connection, token, id, playerId, claimed, "REVIEW", detail.take(512))
            refreshCount(connection, playerId)
        }
    }

    @Deprecated("Use reserveInbox/finishClaim")
    fun updateInboxAmount(playerId: UUID, entryId: Long, remainingAmount: Int): CompletableFuture<Void> = runAsync {
        open().use { connection ->
            val sql = if (remainingAmount <= 0) "DELETE FROM reward_inbox WHERE id=? AND player_uuid=? AND status='PENDING'"
                else "UPDATE reward_inbox SET amount=? WHERE id=? AND player_uuid=? AND status='PENDING'"
            connection.prepareStatement(sql).use { statement ->
                var index = 1; if (remainingAmount > 0) statement.setInt(index++, remainingAmount)
                statement.setLong(index++, entryId); statement.setString(index, playerId.toString()); statement.executeUpdate()
            }
            refreshCount(connection, playerId)
        }
    }

    fun cachedInboxCount(playerId: UUID) = inboxCounts[playerId] ?: 0

    fun refreshInboxCount(playerId: UUID): CompletableFuture<Int> = supplyAsync {
        open().use { connection -> countPending(connection, playerId).also { inboxCounts[playerId] = it } }
    }

    fun recordSessionCheckpoint(checkpoint: SessionCheckpoint): CompletableFuture<Void> = runAsync {
        transaction { connection ->
            val previous = connection.prepareStatement("SELECT seconds,cycles,rewards FROM session_stats_checkpoint WHERE session_id=?").use {
                it.setString(1, checkpoint.sessionId)
                it.executeQuery().use { result -> if (result.next()) longArrayOf(result.getLong(1), result.getLong(2), result.getLong(3)) else longArrayOf(0, 0, 0) }
            }
            val seconds = (checkpoint.seconds - previous[0]).coerceAtLeast(0)
            val cycles = (checkpoint.cycles - previous[1]).coerceAtLeast(0)
            val rewards = (checkpoint.rewards - previous[2]).coerceAtLeast(0)
            connection.prepareStatement("""
                INSERT INTO session_stats_checkpoint(session_id,player_uuid,player_name,pool_id,seconds,cycles,rewards,updated_at)
                VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(session_id) DO UPDATE SET
                player_name=excluded.player_name,seconds=MAX(seconds,excluded.seconds),cycles=MAX(cycles,excluded.cycles),
                rewards=MAX(rewards,excluded.rewards),updated_at=excluded.updated_at
            """.trimIndent()).use {
                it.setString(1, checkpoint.sessionId); it.setString(2, checkpoint.playerId.toString()); it.setString(3, checkpoint.playerName)
                it.setString(4, checkpoint.poolId); it.setLong(5, checkpoint.seconds); it.setLong(6, checkpoint.cycles)
                it.setLong(7, checkpoint.rewards); it.setLong(8, now()); it.executeUpdate()
            }
            if (seconds > 0 || cycles > 0 || rewards > 0) {
                connection.prepareStatement("""
                    INSERT INTO player_stats(player_uuid,pool_id,player_name,total_seconds,cycles,rewards,longest_session,updated_at)
                    VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(player_uuid,pool_id) DO UPDATE SET
                    player_name=excluded.player_name,total_seconds=total_seconds+excluded.total_seconds,
                    cycles=cycles+excluded.cycles,rewards=rewards+excluded.rewards,
                    longest_session=MAX(longest_session,excluded.longest_session),updated_at=excluded.updated_at
                """.trimIndent()).use {
                    it.setString(1, checkpoint.playerId.toString()); it.setString(2, checkpoint.poolId); it.setString(3, checkpoint.playerName)
                    it.setLong(4, seconds); it.setLong(5, cycles); it.setLong(6, rewards); it.setLong(7, checkpoint.seconds); it.setLong(8, now()); it.executeUpdate()
                }
                listOf(StatsPeriod.DAY, StatsPeriod.WEEK, StatsPeriod.MONTH).forEach { period ->
                    connection.prepareStatement("""
                        INSERT INTO player_period_stats(period_type,period_key,player_uuid,pool_id,player_name,seconds,cycles,rewards,updated_at)
                        VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(period_type,period_key,player_uuid,pool_id) DO UPDATE SET
                        player_name=excluded.player_name,seconds=seconds+excluded.seconds,cycles=cycles+excluded.cycles,
                        rewards=rewards+excluded.rewards,updated_at=excluded.updated_at
                    """.trimIndent()).use {
                        it.setString(1, period.name); it.setString(2, periodKey(period)); it.setString(3, checkpoint.playerId.toString())
                        it.setString(4, checkpoint.poolId); it.setString(5, checkpoint.playerName); it.setLong(6, seconds)
                        it.setLong(7, cycles); it.setLong(8, rewards); it.setLong(9, now()); it.executeUpdate()
                    }
                }
                statsCache.keys.removeIf { it.first == checkpoint.playerId }
            }
        }
    }

    fun loadStats(playerId: UUID, period: StatsPeriod): CompletableFuture<PlayerStats> = supplyAsync {
        open().use { connection -> readStats(connection, playerId, period).also { statsCache[playerId to period] = it } }
    }

    fun refreshStats(playerId: UUID): CompletableFuture<List<PlayerStats>> = supplyAsync {
        open().use { connection -> StatsPeriod.entries.map { period -> readStats(connection, playerId, period).also { statsCache[playerId to period] = it } } }
    }

    fun cachedStats(playerId: UUID, period: StatsPeriod) = statsCache[playerId to period]

    fun loadLeaderboard(period: StatsPeriod, limit: Int = 45): CompletableFuture<List<LeaderboardEntry>> = supplyAsync {
        open().use { connection ->
            val (source, where) = statsSource(period)
            connection.prepareStatement("""
                SELECT player_uuid,MAX(player_name),SUM(seconds) AS score,SUM(cycles),SUM(rewards)
                FROM $source $where GROUP BY player_uuid ORDER BY score DESC,player_uuid LIMIT ?
            """.trimIndent()).use { statement ->
                var index = bindPeriod(statement, period)
                statement.setInt(index, limit.coerceIn(1, 100))
                statement.executeQuery().use { result -> buildList {
                    var rank = 1
                    while (result.next()) add(LeaderboardEntry(result.getString(1), result.getString(2), result.getLong(3), result.getLong(4), result.getLong(5), rank++))
                } }
            }
        }
    }

    fun listRewardLogs(playerId: UUID?, status: String?, page: Int, pageSize: Int = 45): CompletableFuture<RewardLogPage> = supplyAsync {
        open().use { connection ->
            val clauses = mutableListOf<String>()
            if (playerId != null) clauses += "player_uuid=?"
            if (!status.isNullOrBlank() && !status.equals("ALL", true)) clauses += "status=?"
            val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
            fun bind(statement: java.sql.PreparedStatement, includePage: Boolean): Int {
                var index = 1
                if (playerId != null) statement.setString(index++, playerId.toString())
                if (!status.isNullOrBlank() && !status.equals("ALL", true)) statement.setString(index++, status.uppercase(Locale.ROOT))
                if (includePage) { statement.setInt(index++, pageSize.coerceIn(1, 45)); statement.setInt(index, page.coerceAtLeast(0) * pageSize.coerceIn(1, 45)) }
                return index
            }
            val total = connection.prepareStatement("SELECT COUNT(*) FROM reward_ledger $where").use { bind(it, false); it.executeQuery().use { result -> result.next(); result.getInt(1) } }
            val safeSize = pageSize.coerceIn(1, 45); val pages = maxOf(1, (total + safeSize - 1) / safeSize); val safePage = page.coerceIn(0, pages - 1)
            val entries = connection.prepareStatement("SELECT settlement_id,player_uuid,pool_id,reward_type,reward_key,amount,status,detail,created_at,updated_at FROM reward_ledger $where ORDER BY created_at DESC LIMIT ? OFFSET ?").use {
                var index = 1
                if (playerId != null) it.setString(index++, playerId.toString())
                if (!status.isNullOrBlank() && !status.equals("ALL", true)) it.setString(index++, status.uppercase(Locale.ROOT))
                it.setInt(index++, safeSize); it.setInt(index, safePage * safeSize)
                it.executeQuery().use { result -> buildList { while (result.next()) add(readRewardLog(result)) } }
            }
            RewardLogPage(entries, safePage, pages, total)
        }
    }

    fun listReviews(page: Int, pageSize: Int = 45): CompletableFuture<ReviewPage> = supplyAsync {
        open().use { connection ->
            val safeSize = pageSize.coerceIn(1, 45)
            val total = connection.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM reward_inbox WHERE status='REVIEW'").use { result -> result.next(); result.getInt(1) } }
            val pages = maxOf(1, (total + safeSize - 1) / safeSize); val safePage = page.coerceIn(0, pages - 1)
            val sql = """
                SELECT r.id,r.player_uuid,r.pool_id,r.provider,r.item_id,r.amount,r.display_name,
                COALESCE(r.claim_token,''),COALESCE((SELECT detail FROM inbox_claim_log l WHERE l.claim_token=r.claim_token ORDER BY l.id DESC LIMIT 1),''),r.created_at
                FROM reward_inbox r WHERE r.status='REVIEW' ORDER BY r.id DESC LIMIT ? OFFSET ?
            """.trimIndent()
            val entries = connection.prepareStatement(sql).use {
                it.setInt(1, safeSize); it.setInt(2, safePage * safeSize)
                it.executeQuery().use { result -> buildList { while (result.next()) add(ReviewEntry(
                    result.getLong(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5),
                    result.getInt(6), result.getString(7), result.getString(8), result.getString(9), Instant.ofEpochSecond(result.getLong(10)),
                )) } }
            }
            ReviewPage(entries, safePage, pages, total)
        }
    }

    fun resolveReview(entryId: Long, action: String, adminName: String): CompletableFuture<Boolean> = supplyAsync {
        transaction { connection ->
            val target = connection.prepareStatement("SELECT player_uuid,claim_token FROM reward_inbox WHERE id=? AND status='REVIEW'").use {
                it.setLong(1, entryId); it.executeQuery().use { result -> if (result.next()) result.getString(1) to result.getString(2) else return@transaction false }
            }
            val normalized = action.uppercase(Locale.ROOT)
            val changed = when (normalized) {
                "RESTORE" -> connection.prepareStatement("UPDATE reward_inbox SET status='PENDING',claim_token=NULL,claim_started_at=NULL WHERE id=? AND status='REVIEW'").use { it.setLong(1, entryId); it.executeUpdate() }
                "CONFIRM", "VOID" -> connection.prepareStatement("DELETE FROM reward_inbox WHERE id=? AND status='REVIEW'").use { it.setLong(1, entryId); it.executeUpdate() }
                else -> throw IllegalArgumentException("Unsupported review action: $action")
            }
            if (changed == 1) {
                insertAudit(connection, adminName, "REVIEW_$normalized", "INBOX", entryId.toString(), "token=${target.second.orEmpty()}")
                refreshCount(connection, UUID.fromString(target.first))
            }
            changed == 1
        }
    }

    fun addAudit(adminName: String, action: String, targetType: String, targetId: String, detail: String = ""): CompletableFuture<Void> = runAsync {
        open().use { insertAudit(it, adminName, action, targetType, targetId, detail.take(512)) }
    }

    fun exportRewardLogs(file: File): CompletableFuture<Int> = supplyAsync {
        file.parentFile?.mkdirs()
        open().use { connection -> Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("settlement_id,player_uuid,pool_id,reward_type,reward_key,amount,status,detail,created_at,updated_at")
            connection.createStatement().use { statement -> statement.executeQuery("SELECT settlement_id,player_uuid,pool_id,reward_type,reward_key,amount,status,detail,created_at,updated_at FROM reward_ledger ORDER BY created_at DESC").use { result ->
                var count = 0
                while (result.next()) {
                    val row = readRewardLog(result)
                    writer.appendLine(listOf(row.settlementId,row.playerId,row.poolId,row.rewardType,row.rewardKey,row.amount,row.status,row.detail,row.createdAt,row.updatedAt).joinToString(",") { csv(it.toString()) })
                    count++
                }
                count
            } }
        } }
    }

    private fun readStats(connection: Connection, playerId: UUID, period: StatsPeriod): PlayerStats {
        val (source, where) = statsSource(period)
        val row = connection.prepareStatement("SELECT COALESCE(MAX(player_name),''),COALESCE(SUM(seconds),0),COALESCE(SUM(cycles),0),COALESCE(SUM(rewards),0) FROM $source $where ${if (where.isBlank()) "WHERE" else "AND"} player_uuid=?").use {
            var index = bindPeriod(it, period); it.setString(index, playerId.toString())
            it.executeQuery().use { result -> result.next(); arrayOf(result.getString(1), result.getLong(2), result.getLong(3), result.getLong(4)) }
        }
        val longest = connection.prepareStatement("SELECT COALESCE(MAX(longest_session),0) FROM player_stats WHERE player_uuid=?").use {
            it.setString(1, playerId.toString()); it.executeQuery().use { result -> result.next(); result.getLong(1) }
        }
        val score = row[1] as Long
        val rank = if (score <= 0) 0 else connection.prepareStatement("""
            SELECT 1+COUNT(*) FROM (SELECT player_uuid,SUM(seconds) score FROM $source $where GROUP BY player_uuid HAVING score>?)
        """.trimIndent()).use {
            var index = bindPeriod(it, period); it.setLong(index, score); it.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
        return PlayerStats(playerId.toString(), (row[0] as String).ifBlank { playerId.toString().take(8) }, period, score, row[2] as Long, row[3] as Long, longest, rank)
    }

    private fun statsSource(period: StatsPeriod): Pair<String, String> = if (period == StatsPeriod.TOTAL) {
        "(SELECT player_uuid,pool_id,player_name,total_seconds AS seconds,cycles,rewards FROM player_stats)" to ""
    } else "player_period_stats" to "WHERE period_type=? AND period_key=?"

    private fun bindPeriod(statement: java.sql.PreparedStatement, period: StatsPeriod): Int {
        var index = 1
        if (period != StatsPeriod.TOTAL) { statement.setString(index++, period.name); statement.setString(index++, periodKey(period)) }
        return index
    }

    private fun periodKey(period: StatsPeriod): String {
        val zone = runCatching { ZoneId.of(statisticsTimeZone()) }.getOrDefault(ZoneId.systemDefault())
        val date = LocalDate.now(zone)
        return when (period) {
            StatsPeriod.DAY -> date.toString()
            StatsPeriod.WEEK -> "%04d-W%02d".format(date.get(WeekFields.ISO.weekBasedYear()), date.get(WeekFields.ISO.weekOfWeekBasedYear()))
            StatsPeriod.MONTH -> "%04d-%02d".format(date.year, date.monthValue)
            StatsPeriod.TOTAL -> "total"
        }
    }

    private fun readRewardLog(result: ResultSet) = RewardLogEntry(
        result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5),
        result.getDouble(6), result.getString(7), result.getString(8), Instant.ofEpochSecond(result.getLong(9)), Instant.ofEpochSecond(result.getLong(10)),
    )

    private fun insertAudit(connection: Connection, adminName: String, action: String, targetType: String, targetId: String, detail: String) {
        connection.prepareStatement("INSERT INTO admin_audit_log(admin_name,action,target_type,target_id,detail,created_at) VALUES(?,?,?,?,?,?)").use {
            it.setString(1, adminName); it.setString(2, action); it.setString(3, targetType); it.setString(4, targetId)
            it.setString(5, detail); it.setLong(6, now()); it.executeUpdate()
        }
    }
    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""

    private fun selectInbox(connection: Connection, playerId: UUID, limit: Int, offset: Int): List<InboxEntry> =
        connection.prepareStatement("SELECT id,pool_id,provider,item_id,amount,display_name,created_at FROM reward_inbox WHERE player_uuid=? AND status='PENDING' ORDER BY id LIMIT ? OFFSET ?").use { statement ->
            statement.setString(1, playerId.toString()); statement.setInt(2, limit); statement.setInt(3, offset)
            statement.executeQuery().use { result -> buildList { while (result.next()) add(readEntry(result)) } }
        }

    private fun selectEntry(connection: Connection, playerId: UUID, id: Long): InboxEntry? =
        connection.prepareStatement("SELECT id,pool_id,provider,item_id,amount,display_name,created_at FROM reward_inbox WHERE id=? AND player_uuid=? AND status='PENDING'").use { statement ->
            statement.setLong(1, id); statement.setString(2, playerId.toString())
            statement.executeQuery().use { if (it.next()) readEntry(it) else null }
        }

    private fun readEntry(result: ResultSet) = InboxEntry(
        result.getLong("id"), result.getString("pool_id"), result.getString("provider"), result.getString("item_id"),
        result.getInt("amount"), result.getString("display_name"), Instant.ofEpochSecond(result.getLong("created_at")),
    )

    private fun insertInbox(connection: Connection, playerId: UUID, poolId: String, provider: String, itemId: String, amount: Int, displayName: String): Long =
        connection.prepareStatement("INSERT INTO reward_inbox(player_uuid,pool_id,provider,item_id,amount,display_name,created_at,status) VALUES(?,?,?,?,?,?,?,'PENDING')", Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, playerId.toString()); statement.setString(2, poolId); statement.setString(3, provider)
            statement.setString(4, itemId); statement.setInt(5, amount); statement.setString(6, displayName); statement.setLong(7, now())
            statement.executeUpdate(); statement.generatedKeys.use { if (it.next()) it.getLong(1) else throw SQLException("No generated inbox id") }
        }

    private fun insertLedger(connection: Connection, id: String, playerId: UUID, poolId: String, type: String, key: String, amount: Double, status: String): Boolean =
        connection.prepareStatement("INSERT OR IGNORE INTO reward_ledger(settlement_id,player_uuid,pool_id,reward_type,reward_key,amount,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)").use {
            it.setString(1, id); it.setString(2, playerId.toString()); it.setString(3, poolId); it.setString(4, type)
            it.setString(5, key); it.setDouble(6, amount); it.setString(7, status); it.setLong(8, now()); it.setLong(9, now())
            it.executeUpdate() == 1
        }

    private fun updateLedger(connection: Connection, id: String, status: String, detail: String) {
        connection.prepareStatement("UPDATE reward_ledger SET status=?,detail=?,updated_at=? WHERE settlement_id=?").use {
            it.setString(1, status); it.setString(2, detail); it.setLong(3, now()); it.setString(4, id); it.executeUpdate()
        }
    }

    private fun claimInboxId(connection: Connection, playerId: UUID, token: String): Long? =
        connection.prepareStatement("SELECT id FROM reward_inbox WHERE player_uuid=? AND claim_token=? AND status='CLAIMING'").use {
            it.setString(1, playerId.toString()); it.setString(2, token); it.executeQuery().use { result -> if (result.next()) result.getLong(1) else null }
        }

    private fun insertClaimLog(connection: Connection, token: String, inboxId: Long, playerId: UUID, amount: Int, status: String, detail: String) {
        connection.prepareStatement("INSERT INTO inbox_claim_log(claim_token,inbox_id,player_uuid,claimed_amount,status,detail,created_at) VALUES(?,?,?,?,?,?,?)").use {
            it.setString(1, token); it.setLong(2, inboxId); it.setString(3, playerId.toString()); it.setInt(4, amount)
            it.setString(5, status); it.setString(6, detail); it.setLong(7, now()); it.executeUpdate()
        }
    }

    private fun countPending(connection: Connection, playerId: UUID): Int = connection.prepareStatement("SELECT COUNT(*) FROM reward_inbox WHERE player_uuid=? AND status='PENDING'").use {
        it.setString(1, playerId.toString()); it.executeQuery().use { result -> result.next(); result.getInt(1) }
    }
    private fun refreshCount(connection: Connection, playerId: UUID) { inboxCounts[playerId] = countPending(connection, playerId) }

    private fun addColumn(connection: Connection, table: String, column: String, definition: String) {
        val exists = connection.createStatement().use { statement -> statement.executeQuery("PRAGMA table_info($table)").use { result ->
            var found = false; while (result.next()) if (result.getString("name").equals(column, true)) found = true; found
        } }
        if (!exists) connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
    }

    private fun open(): Connection = DriverManager.getConnection(jdbcUrl).also { connection ->
        connection.createStatement().use { it.execute("PRAGMA busy_timeout=5000"); it.execute("PRAGMA foreign_keys=ON") }
    }

    private fun <T> transaction(action: (Connection) -> T): T = open().use { connection ->
        connection.autoCommit = false
        try { action(connection).also { connection.commit() } }
        catch (throwable: Throwable) { connection.rollback(); throw throwable }
    }

    private fun runAsync(action: () -> Unit): CompletableFuture<Void> = CompletableFuture.runAsync({ sqlGuard(action) }, executor)
    private fun <T> supplyAsync(action: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({ sqlGuard(action) }, executor)
    private fun <T> sqlGuard(action: () -> T): T = try { action() } catch (exception: SQLException) {
        logger.log(Level.SEVERE, "SQLite operation failed", exception); throw IllegalStateException(exception)
    }

    override fun close() {
        executor.shutdown()
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) logger.warning("SQLite executor did not stop within 5 seconds.") }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun now() = Instant.now().epochSecond
}
