package top.cnuo.idlepool.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class SqliteStoreV12Test {
    @TempDir
    lateinit var temporaryDirectory: Path

    private lateinit var database: Path
    private lateinit var store: SqliteStore

    @BeforeEach
    fun setUp() {
        database = temporaryDirectory.resolve("idlepool.db")
        store = SqliteStore(database.toFile(), statisticsTimeZone = "Asia/Shanghai")
        store.initialize()
    }

    @AfterEach
    fun tearDown() {
        store.close()
    }

    @Test
    fun `schema migration and session checkpoints are idempotent`() {
        val player = UUID.randomUUID()
        store.saveProgress(player, "spawn", ProgressRecord(12, 120, 999, 4, 7)).join()
        assertEquals(7, store.loadProgress(player, "spawn").join().pityCount)

        val checkpoint = SessionCheckpoint("session-1", player, "Tester", "spawn", 60, 2, 3)
        store.recordSessionCheckpoint(checkpoint).join()
        store.recordSessionCheckpoint(checkpoint).join()

        val total = store.loadStats(player, StatsPeriod.TOTAL).join()
        assertEquals(60, total.seconds)
        assertEquals(2, total.cycles)
        assertEquals(3, total.rewards)
        assertEquals(60, total.longestSession)
        assertEquals("Tester", store.loadLeaderboard(StatsPeriod.TOTAL).join().single().playerName)

        DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT meta_value FROM idlepool_meta WHERE meta_key='schema_version'").use {
                    assertTrue(it.next())
                    assertEquals("3", it.getString(1))
                }
            }
        }
    }

    @Test
    fun `review workflow and reward export preserve audit state`() {
        val player = UUID.randomUUID()
        assertTrue(store.enqueueItemSettlement("settlement-1", player, "spawn", "vanilla", "DIAMOND", 2, "Diamond").join())
        val inboxId = store.listInboxPage(player, 0, 45).join().entries.single().id
        val reservation = store.reserveInbox(player, inboxId).join()!!
        store.markClaimReview(player, reservation.token, 0, "uncertain delivery").join()

        val review = store.listReviews(0).join().entries.single()
        assertEquals(inboxId, review.id)
        assertTrue(store.resolveReview(inboxId, "RESTORE", "JUnit").join())
        assertFalse(store.listReviews(0).join().entries.any())
        assertEquals(1, store.listInboxPage(player, 0, 45).join().totalEntries)

        val log = store.listRewardLogs(player, "SUCCESS", 0).join()
        assertEquals(1, log.totalEntries)
        val export = temporaryDirectory.resolve("reward-log.csv").toFile()
        assertEquals(1, store.exportRewardLogs(export).join())
        assertTrue(export.readText().contains("DIAMOND"))
    }
}
