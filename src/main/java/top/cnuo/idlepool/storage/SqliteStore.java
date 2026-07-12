package top.cnuo.idlepool.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class SqliteStore implements AutoCloseable {
    private final JavaPlugin plugin;
    private final String jdbcUrl;
    private final ExecutorService executor;

    public SqliteStore(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create database directory: " + parent);
        }
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "IdlePool-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_pool_progress (
                            player_uuid TEXT NOT NULL,
                            pool_id TEXT NOT NULL,
                            progress_seconds INTEGER NOT NULL,
                            total_seconds INTEGER NOT NULL,
                            expires_at INTEGER NOT NULL,
                            reward_sequence INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            PRIMARY KEY (player_uuid, pool_id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS reward_inbox (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid TEXT NOT NULL,
                            pool_id TEXT NOT NULL,
                            provider TEXT NOT NULL,
                            item_id TEXT NOT NULL,
                            amount INTEGER NOT NULL,
                            display_name TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            status TEXT NOT NULL DEFAULT 'PENDING'
                        )
                        """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_reward_inbox_player ON reward_inbox(player_uuid, status, id)");
            }
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("Cannot initialize SQLite", exception);
        }
    }

    public CompletableFuture<ProgressRecord> loadProgress(UUID playerId, String poolId) {
        return supplyAsync(() -> {
            String sql = "SELECT progress_seconds, total_seconds, expires_at, reward_sequence "
                    + "FROM player_pool_progress WHERE player_uuid = ? AND pool_id = ?";
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, poolId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return ProgressRecord.empty();
                    }
                    return new ProgressRecord(
                            result.getLong("progress_seconds"),
                            result.getLong("total_seconds"),
                            result.getLong("expires_at"),
                            result.getLong("reward_sequence")
                    );
                }
            }
        });
    }

    public CompletableFuture<Void> saveProgress(UUID playerId, String poolId, ProgressRecord progress) {
        return runAsync(() -> {
            String sql = """
                    INSERT INTO player_pool_progress
                        (player_uuid, pool_id, progress_seconds, total_seconds, expires_at, reward_sequence, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid, pool_id) DO UPDATE SET
                        progress_seconds = excluded.progress_seconds,
                        total_seconds = excluded.total_seconds,
                        expires_at = excluded.expires_at,
                        reward_sequence = excluded.reward_sequence,
                        updated_at = excluded.updated_at
                    """;
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, poolId);
                statement.setLong(3, progress.progressSeconds());
                statement.setLong(4, progress.totalSeconds());
                statement.setLong(5, progress.expiresAtEpochSecond());
                statement.setLong(6, progress.rewardSequence());
                statement.setLong(7, Instant.now().getEpochSecond());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Long> enqueueItem(
            UUID playerId,
            String poolId,
            String provider,
            String itemId,
            int amount,
            String displayName
    ) {
        return supplyAsync(() -> {
            String sql = "INSERT INTO reward_inbox "
                    + "(player_uuid, pool_id, provider, item_id, amount, display_name, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, poolId);
                statement.setString(3, provider);
                statement.setString(4, itemId);
                statement.setInt(5, amount);
                statement.setString(6, displayName);
                statement.setLong(7, Instant.now().getEpochSecond());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
                throw new SQLException("No generated inbox id");
            }
        });
    }

    public CompletableFuture<List<InboxEntry>> listInbox(UUID playerId, int limit) {
        return supplyAsync(() -> {
            String sql = "SELECT id, pool_id, provider, item_id, amount, display_name, created_at "
                    + "FROM reward_inbox WHERE player_uuid = ? AND status = 'PENDING' ORDER BY id LIMIT ?";
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<InboxEntry> entries = new ArrayList<>();
                    while (result.next()) {
                        entries.add(new InboxEntry(
                                result.getLong("id"),
                                result.getString("pool_id"),
                                result.getString("provider"),
                                result.getString("item_id"),
                                result.getInt("amount"),
                                result.getString("display_name"),
                                Instant.ofEpochSecond(result.getLong("created_at"))
                        ));
                    }
                    return entries;
                }
            }
        });
    }

    public CompletableFuture<Void> updateInboxAmount(UUID playerId, long entryId, int remainingAmount) {
        return runAsync(() -> {
            String sql = remainingAmount <= 0
                    ? "DELETE FROM reward_inbox WHERE id = ? AND player_uuid = ? AND status = 'PENDING'"
                    : "UPDATE reward_inbox SET amount = ? WHERE id = ? AND player_uuid = ? AND status = 'PENDING'";
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (remainingAmount > 0) {
                    statement.setInt(index++, remainingAmount);
                }
                statement.setLong(index++, entryId);
                statement.setString(index, playerId.toString());
                statement.executeUpdate();
            }
        });
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return connection;
    }

    private CompletableFuture<Void> runAsync(SqlRunnable action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "SQLite operation failed", exception);
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "SQLite operation failed", exception);
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("SQLite executor did not stop within 5 seconds.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
