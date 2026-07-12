package top.cnuo.idlepool.storage

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class SqliteStore(private val plugin: JavaPlugin, databaseFile: File) : AutoCloseable {
    private val jdbcUrl: String
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "IdlePool-Storage").apply { isDaemon = true }
    }
    private val inboxCounts = ConcurrentHashMap<UUID, Int>()

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
                    statement.execute("INSERT INTO idlepool_meta(meta_key, meta_value) VALUES ('schema_version', '2') ON CONFLICT(meta_key) DO UPDATE SET meta_value='2'")
                    // Unknown outcome after a crash is never returned to PENDING automatically: this prevents duplication.
                    statement.execute("UPDATE reward_inbox SET status='REVIEW' WHERE status='CLAIMING'")
                    val retentionDays = plugin.config.getInt("storage.reward-log-retention-days", 90).coerceAtLeast(1)
                    val cutoff = now() - TimeUnit.DAYS.toSeconds(retentionDays.toLong())
                    connection.prepareStatement("DELETE FROM reward_ledger WHERE created_at < ? AND status IN ('SUCCESS','FAILED')").use {
                        it.setLong(1, cutoff)
                        it.executeUpdate()
                    }
                    connection.prepareStatement("DELETE FROM inbox_claim_log WHERE created_at < ?").use {
                        it.setLong(1, cutoff)
                        it.executeUpdate()
                    }
                }
            }
        } catch (exception: Exception) {
            throw IllegalStateException("Cannot initialize SQLite", exception)
        }
    }

    fun loadProgress(playerId: UUID, poolId: String): CompletableFuture<ProgressRecord> = supplyAsync {
        open().use { connection -> connection.prepareStatement(
            "SELECT progress_seconds,total_seconds,expires_at,reward_sequence FROM player_pool_progress WHERE player_uuid=? AND pool_id=?"
        ).use { statement ->
            statement.setString(1, playerId.toString()); statement.setString(2, poolId)
            statement.executeQuery().use { result ->
                if (!result.next()) ProgressRecord.empty() else ProgressRecord(
                    result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4)
                )
            }
        } }
    }

    fun saveProgress(playerId: UUID, poolId: String, progress: ProgressRecord): CompletableFuture<Void> = runAsync {
        open().use { connection -> connection.prepareStatement("""
            INSERT INTO player_pool_progress(player_uuid,pool_id,progress_seconds,total_seconds,expires_at,reward_sequence,updated_at)
            VALUES(?,?,?,?,?,?,?) ON CONFLICT(player_uuid,pool_id) DO UPDATE SET
            progress_seconds=excluded.progress_seconds,total_seconds=excluded.total_seconds,
            expires_at=excluded.expires_at,reward_sequence=excluded.reward_sequence,updated_at=excluded.updated_at
        """.trimIndent()).use { statement ->
            statement.setString(1, playerId.toString()); statement.setString(2, poolId)
            statement.setLong(3, progress.progressSeconds); statement.setLong(4, progress.totalSeconds)
            statement.setLong(5, progress.expiresAtEpochSecond); statement.setLong(6, progress.rewardSequence)
            statement.setLong(7, now()); statement.executeUpdate()
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
        plugin.logger.log(Level.SEVERE, "SQLite operation failed", exception); throw IllegalStateException(exception)
    }

    override fun close() {
        executor.shutdown()
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) plugin.logger.warning("SQLite executor did not stop within 5 seconds.") }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun now() = Instant.now().epochSecond
}
