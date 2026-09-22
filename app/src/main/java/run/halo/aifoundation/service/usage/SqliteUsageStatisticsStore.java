package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageError;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sqlite.JDBC;

@Slf4j
@Component
public class SqliteUsageStatisticsStore implements UsageStatisticsStore {

    static final int BUSY_TIMEOUT_MILLIS = 5_000;
    static final long WAL_SIZE_LIMIT_BYTES = 16L * 1024 * 1024;
    private static final int MAX_CONCURRENT_READERS = 4;

    private static final AtomicInteger LIVE_STORES = new AtomicInteger();

    private final UsageDatabasePaths paths;
    private final UsageStatisticsMaintenance maintenance;
    private final UsageStatisticsQueryRepository queries;
    private final Semaphore readerPermits = new Semaphore(MAX_CONCURRENT_READERS, true);
    private final java.util.Set<Connection> activeReaders = ConcurrentHashMap.newKeySet();
    private final Object readerLifecycle = new Object();
    private final java.util.ArrayDeque<Connection> idleReaders = new java.util.ArrayDeque<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    // Snapshot publication must not overlap reset, but need not stop ordinary writes.
    private final java.util.concurrent.locks.ReentrantReadWriteLock snapshotAccess =
        new java.util.concurrent.locks.ReentrantReadWriteLock();
    private Connection writer;
    private volatile boolean initialized;

    @Autowired
    public SqliteUsageStatisticsStore(UsageDatabasePaths paths) {
        this(paths, new UsageStatisticsMaintenance(), new UsageStatisticsQueryRepository());
    }

    SqliteUsageStatisticsStore(UsageDatabasePaths paths, UsageStatisticsMaintenance maintenance,
        UsageStatisticsQueryRepository queries) {
        this.paths = paths;
        this.maintenance = maintenance;
        this.queries = queries;
    }

    @Override
    public synchronized void initialize() {
        if (closing.get()) {
            throw new IllegalStateException("AI usage statistics store is closed");
        }
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(paths.database().getParent());
            Files.createDirectories(paths.backupDirectory());
            ensureDriverRegistered();
            var recovery = UsageSqliteFiles.recoverIfRequired(paths);
            writer = openConnection();
            configureWriter(writer);
            UsageSqliteSchema.migrate(writer, paths);
            UsageSqliteSchema.validateRecognized(writer);
            var integrity = quickCheck(writer);
            if (!"ok".equalsIgnoreCase(integrity)) {
                throw new UsageDatabaseIntegrityException(
                    "SQLite quick_check failed: " + integrity);
            }
            if (recovery.restored()) {
                var recoveredAt = System.currentTimeMillis();
                try (var statement = writer.prepareStatement("""
                    UPDATE ai_statistics_health SET integrity_error = ?,
                      affected_since_ms = COALESCE(affected_since_ms, ?),
                      affected_until_ms = MAX(COALESCE(affected_until_ms, 0), ?)
                    WHERE id = 1
                    """)) {
                    statement.setString(1, "RECOVERED_FROM_SNAPSHOT");
                    statement.setLong(2, recovery.snapshotAt().toEpochMilli());
                    statement.setLong(3, recoveredAt);
                    statement.executeUpdate();
                }
            }
            initialized = true;
            LIVE_STORES.incrementAndGet();
        } catch (Exception | LinkageError error) {
            closeSilently();
            if (LIVE_STORES.get() == 0) {
                deregisterPluginDrivers();
            }
            throw new IllegalStateException("Failed to initialize AI usage statistics", error);
        }
    }

    @Override
    public synchronized long currentEpoch() {
        requireInitialized();
        return longMeta(writer, "statistics_epoch", 1L);
    }

    @Override
    public synchronized UsageHealthState readHealth() {
        requireInitialized();
        try (var statement = writer.prepareStatement(
            "SELECT * FROM ai_statistics_health WHERE id = 1");
            var row = statement.executeQuery()) {
            if (!row.next()) {
                return UsageHealthState.empty();
            }
            return new UsageHealthState(row.getLong("dropped_events"),
                row.getLong("incomplete_calls"), row.getLong("write_failures"),
                instant(row, "last_write_error_at_ms"), instant(row, "affected_since_ms"),
                instant(row, "affected_until_ms"),
                row.getString("migration_error"), row.getString("integrity_error"));
        } catch (SQLException error) {
            throw databaseError("read statistics health", error);
        }
    }

    @Override
    public synchronized void writeHealth(UsageHealthState health) {
        requireInitialized();
        var sql = """
            UPDATE ai_statistics_health SET affected_since_ms = ?, affected_until_ms = ?,
              last_write_error_at_ms = ?, dropped_events = ?, incomplete_calls = ?,
              write_failures = ?, migration_error = ?, integrity_error = ? WHERE id = 1
            """;
        try (var statement = writer.prepareStatement(sql)) {
            setInstant(statement, 1, health.affectedSince());
            setInstant(statement, 2, health.affectedUntil());
            setInstant(statement, 3, health.lastWriteErrorAt());
            statement.setLong(4, health.droppedEvents());
            statement.setLong(5, health.incompleteCalls());
            statement.setLong(6, health.writeFailures());
            statement.setString(7, health.migrationError());
            statement.setString(8, health.integrityError());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw databaseError("write statistics health", error);
        }
    }

    @Override
    public synchronized void writeBatch(List<Runnable> writes) {
        requireInitialized();
        var restoreAutoCommit = true;
        try {
            writer.setAutoCommit(false);
            writes.forEach(Runnable::run);
            writer.commit();
        } catch (RuntimeException | SQLException error) {
            restoreAutoCommit = UsageSqliteTransactions.rollback(writer, error);
            throw new IllegalStateException("Failed to write statistics batch", error);
        } catch (Error error) {
            // Roll back even fatal failures, then propagate them without retrying here.
            restoreAutoCommit = UsageSqliteTransactions.rollback(writer, error);
            throw error;
        } finally {
            if (restoreAutoCommit) {
                setAutoCommit(writer, true);
            }
        }
    }

    @Override
    public synchronized void startCall(UsageCallStart start) {
        requireInitialized();
        if (start.epoch() != currentEpoch()) {
            return;
        }
        insertStart(writer, start);
    }

    @Override
    public synchronized void recordExecution(UsageExecutionRecord execution) {
        requireInitialized();
        if (execution.epoch() != currentEpoch()) {
            return;
        }
        var sql = """
            INSERT INTO ai_model_executions (
              id, call_id, epoch, unit_kind, unit_index, attempt_index,
              started_at_ms, completed_at_ms, status, error_type, error_code,
              request_model_id, response_model_id,
              input_tokens, output_tokens, cache_read_input_tokens,
              cache_creation_input_tokens, reasoning_output_tokens,
              provider_total_tokens, accounted_total_tokens, usage_quality
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(call_id, unit_kind, unit_index, attempt_index) DO NOTHING
            """;
        try (var statement = writer.prepareStatement(sql)) {
            var index = 1;
            statement.setString(index++, execution.id());
            statement.setString(index++, execution.callId());
            statement.setLong(index++, execution.epoch());
            statement.setString(index++, execution.unitKind().name());
            statement.setInt(index++, execution.unitIndex());
            statement.setInt(index++, execution.attemptIndex());
            statement.setLong(index++, execution.startedAt().toEpochMilli());
            setInstant(statement, index++, execution.completedAt());
            statement.setString(index++, execution.status().name());
            statement.setString(index++, value(execution.error(), UsageError::type));
            statement.setString(index++, value(execution.error(), UsageError::code));
            statement.setString(index++, execution.requestModelId());
            statement.setString(index++, execution.responseModelId());
            index = bindUsage(statement, index, execution.usage());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw databaseError("record execution", error);
        }
    }

    @Override
    public synchronized void finishCall(UsageCallTerminal terminal) {
        requireInitialized();
        if (terminal.epoch() != currentEpoch()) {
            return;
        }
        insertStart(writer, terminal.start());
        // A cache-only stream can finish its text projection before final result metadata is read.
        // Only that missing, execution-free success may be enriched; known terminals stay immutable.
        var sql = """
            UPDATE ai_calls SET
              completed_at_ms = ?, duration_ms = ?, status = ?, error_type = ?, error_code = ?,
              response_model_id = ?, step_count = ?, attempt_count = ?,
              missing_execution_count = ?, complete = ?, input_tokens = ?, output_tokens = ?,
              cache_read_input_tokens = ?, cache_creation_input_tokens = ?,
              reasoning_output_tokens = ?, provider_total_tokens = ?,
              accounted_total_tokens = ?, usage_quality = ?
            WHERE id = ? AND epoch = ? AND (
              status = 'IN_PROGRESS' OR (
                streaming = 1 AND attempt_count = 0 AND status = 'SUCCEEDED'
                AND usage_quality = 'MISSING'
              )
            )
            """;
        try (var statement = writer.prepareStatement(sql)) {
            var index = 1;
            statement.setLong(index++, terminal.completedAt().toEpochMilli());
            var duration = terminal.completedAt().toEpochMilli()
                - terminal.start().startedAt().toEpochMilli();
            statement.setLong(index++, Math.max(0, duration));
            statement.setString(index++, terminal.status().name());
            statement.setString(index++, value(terminal.error(), UsageError::type));
            statement.setString(index++, value(terminal.error(), UsageError::code));
            statement.setString(index++, terminal.responseModelId());
            statement.setInt(index++, terminal.stepCount());
            statement.setInt(index++, terminal.attemptCount());
            statement.setInt(index++, terminal.missingExecutionCount());
            statement.setInt(index++, terminal.complete() ? 1 : 0);
            index = bindUsage(statement, index, terminal.usage());
            statement.setString(index++, terminal.callId());
            statement.setLong(index, terminal.epoch());
            statement.executeUpdate();
            if (terminal.status() == run.halo.aifoundation.service.observation.UsageStatus.TIMED_OUT) {
                try (var cancelled = writer.prepareStatement(
                    "UPDATE ai_model_executions SET status = 'TIMED_OUT', error_type = 'TIMEOUT'"
                        + " WHERE call_id = ? AND status = 'CANCELLED'")) {
                    cancelled.setString(1, terminal.callId());
                    cancelled.executeUpdate();
                }
            }
        } catch (SQLException error) {
            throw databaseError("finish call", error);
        }
    }

    @Override
    public UsageSummary summary(UsageQuery query, boolean complete) {
        requireInitialized();
        return withReader(connection -> queries.summary(connection, query, complete));
    }

    @Override
    public List<UsageTrendPoint> trends(UsageQuery query, boolean complete) {
        requireInitialized();
        return withReader(connection -> queries.trends(connection, query, complete));
    }

    @Override
    public UsageCallPage listCalls(UsageQuery query, int size, String cursor) {
        requireInitialized();
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
        return withReader(connection -> queries.listCalls(connection, query, size, cursor));
    }

    @Override
    public Optional<UsageCallDetail> getCall(String id) {
        requireInitialized();
        return withReader(connection -> queries.getCall(connection, id));
    }

    @Override
    public synchronized long reset() {
        requireInitialized();
        var snapshotLock = snapshotAccess.writeLock();
        snapshotLock.lock();
        var restoreAutoCommit = true;
        try {
            writer.setAutoCommit(false);
            try (var statement = writer.createStatement()) {
                statement.executeUpdate("DELETE FROM ai_model_executions");
                statement.executeUpdate("DELETE FROM ai_calls");
                statement.executeUpdate("DELETE FROM ai_usage_daily");
                statement.executeUpdate("""
                    UPDATE ai_statistics_health SET affected_since_ms = NULL,
                      affected_until_ms = NULL, last_write_error_at_ms = NULL,
                      dropped_events = 0, incomplete_calls = 0, write_failures = 0,
                      migration_error = NULL, integrity_error = NULL
                    WHERE id = 1
                    """);
            }
            // Remove all recoverable pre-reset snapshots before committing the reset.
            // A crash may lose backup availability, but cannot restore deleted history.
            for (var snapshot : UsageSqliteFiles.listBackups(paths)) {
                try {
                    Files.delete(snapshot);
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Failed to retire pre-reset snapshot", error);
                }
            }
            try (var statement = writer.createStatement()) {
                statement.executeUpdate("DELETE FROM ai_statistics_meta WHERE key NOT IN ('statistics_epoch', 'schema_version')");
            }
            var nextEpoch = currentEpoch() + 1;
            putMeta(writer, "statistics_epoch", Long.toString(nextEpoch));
            writer.commit();
            return nextEpoch;
        } catch (RuntimeException | SQLException error) {
            restoreAutoCommit = UsageSqliteTransactions.rollback(writer, error);
            throw new IllegalStateException("Failed to reset statistics", error);
        } catch (Error error) {
            restoreAutoCommit = UsageSqliteTransactions.rollback(writer, error);
            throw error;
        } finally {
            if (restoreAutoCommit) {
                setAutoCommit(writer, true);
            }
            snapshotLock.unlock();
        }
    }

    @Override
    public synchronized void reconcileAbandoned(Instant now) {
        requireInitialized();
        var sql = """
            WITH recovered AS MATERIALIZED (
              SELECT call_id, COUNT(*) attempts,
                MAX(CASE WHEN unit_kind = 'GENERATION_STEP' THEN unit_index + 1 ELSE 0 END) steps,
                SUM(CASE WHEN usage_quality = 'MISSING' THEN 1 ELSE 0 END) missing,
                SUM(input_tokens) input_tokens,
                SUM(output_tokens) output_tokens,
                SUM(cache_read_input_tokens) cache_read_input_tokens,
                SUM(cache_creation_input_tokens) cache_creation_input_tokens,
                SUM(reasoning_output_tokens) reasoning_output_tokens,
                SUM(provider_total_tokens) provider_total_tokens,
                SUM(accounted_total_tokens) accounted_total_tokens
              FROM ai_model_executions
              WHERE call_id IN (SELECT id FROM ai_calls WHERE status = 'IN_PROGRESS')
              GROUP BY call_id
            )
            UPDATE ai_calls SET status = 'ABANDONED', completed_at_ms = ?,
              duration_ms = MAX(0, ? - started_at_ms), complete = 0,
              attempt_count = COALESCE((SELECT attempts FROM recovered WHERE call_id = ai_calls.id), 0),
              step_count = COALESCE((SELECT steps FROM recovered WHERE call_id = ai_calls.id), 0),
              missing_execution_count = COALESCE((SELECT missing FROM recovered WHERE call_id = ai_calls.id), 0),
              input_tokens = (SELECT input_tokens FROM recovered WHERE call_id = ai_calls.id),
              output_tokens = (SELECT output_tokens FROM recovered WHERE call_id = ai_calls.id),
              cache_read_input_tokens = (SELECT cache_read_input_tokens FROM recovered WHERE call_id = ai_calls.id),
              cache_creation_input_tokens = (SELECT cache_creation_input_tokens FROM recovered WHERE call_id = ai_calls.id),
              reasoning_output_tokens = (SELECT reasoning_output_tokens FROM recovered WHERE call_id = ai_calls.id),
              provider_total_tokens = (SELECT provider_total_tokens FROM recovered WHERE call_id = ai_calls.id),
              accounted_total_tokens = (SELECT accounted_total_tokens FROM recovered WHERE call_id = ai_calls.id),
              usage_quality = CASE WHEN EXISTS (
                SELECT 1 FROM ai_model_executions
                WHERE call_id = ai_calls.id AND usage_quality <> 'MISSING'
              ) THEN 'PARTIAL' ELSE 'MISSING' END,
              response_model_id = (SELECT response_model_id FROM ai_model_executions
                WHERE call_id = ai_calls.id AND response_model_id IS NOT NULL
                ORDER BY completed_at_ms DESC, id DESC LIMIT 1)
            WHERE status = 'IN_PROGRESS'
            """;
        try (var statement = writer.prepareStatement(sql)) {
            statement.setLong(1, now.toEpochMilli());
            statement.setLong(2, now.toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw databaseError("reconcile abandoned calls", error);
        }
    }

    @Override
    public void rollupAndRetain(Clock clock) {
        while (rollupAndRetainBatch(clock)) {
            // Offline callers may finish maintenance synchronously; the service yields per batch.
        }
    }

    @Override
    public synchronized boolean rollupAndRetainBatch(Clock clock) {
        requireInitialized();
        return maintenance.rollupAndRetainBatch(writer, clock);
    }

    @Override
    public void backup() {
        var snapshotLock = snapshotAccess.readLock();
        snapshotLock.lock();
        try {
            requireInitialized();
            withConnection(connection -> {
                UsageSqliteFiles.backup(connection, paths);
                return null;
            });
        } finally {
            snapshotLock.unlock();
        }
    }

    @Override
    public void close() {
        if (!initialized || !closing.compareAndSet(false, true)) {
            return;
        }
        initialized = false;
        final List<Connection> readers;
        synchronized (readerLifecycle) {
            var connections = new java.util.ArrayList<>(activeReaders);
            connections.addAll(idleReaders);
            idleReaders.clear();
            readers = List.copyOf(connections);
        }
        // JDBC close waits for native statements; interrupt them before acquiring that lock.
        readers.forEach(SqliteUsageStatisticsStore::interrupt);
        interrupt(writer);
        readers.forEach(SqliteUsageStatisticsStore::close);
        boolean acquired = false;
        try {
            acquired = readerPermits.tryAcquire(MAX_CONCURRENT_READERS, BUSY_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            log.warn("Timed out waiting for AI usage statistics readers after closing them");
        }
        closeSilently();
        if (acquired) {
            readerPermits.release(MAX_CONCURRENT_READERS);
        }
        if (LIVE_STORES.decrementAndGet() == 0) {
            deregisterPluginDrivers();
        }
    }

    private Connection openConnection() throws SQLException {
        var connection = DriverManager.getConnection("jdbc:sqlite:" + paths.database());
        try {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MILLIS);
                statement.execute("PRAGMA wal_autocheckpoint = 1000");
                statement.execute("PRAGMA journal_size_limit = " + WAL_SIZE_LIMIT_BYTES);
            }
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
        return connection;
    }

    private void configureWriter(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("PRAGMA journal_mode = WAL")) {
            if (!rows.next() || !"wal".equalsIgnoreCase(rows.getString(1))) {
                throw new SQLException("SQLite did not enter WAL journal mode");
            }
        }
    }

    private void insertStart(Connection connection, UsageCallStart start) {
        var sql = """
            INSERT INTO ai_calls (
              id, epoch, started_at_ms, caller_plugin_name, caller_plugin_version,
              caller_detection_source, feature, operation, model_type, model_name,
              provider_name, provider_type, request_model_id, streaming, status,
              complete, usage_quality
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', 1, 'MISSING')
            ON CONFLICT(id) DO NOTHING
            """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, start.id());
            statement.setLong(2, start.epoch());
            statement.setLong(3, start.startedAt().toEpochMilli());
            statement.setString(4, start.callerPluginName());
            statement.setString(5, start.callerPluginVersion());
            statement.setString(6, start.callerDetectionSource());
            statement.setString(7, start.feature());
            statement.setString(8, start.operation());
            statement.setString(9, start.modelType());
            statement.setString(10, start.modelName());
            statement.setString(11, start.providerName());
            statement.setString(12, start.providerType());
            statement.setString(13, start.requestModelId());
            statement.setInt(14, start.streaming() ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw databaseError("start call", error);
        }
    }

    private String quickCheck(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
            var rows = statement.executeQuery("PRAGMA quick_check")) {
            return rows.next() ? rows.getString(1) : "no-result";
        }
    }

    private <T> T withReader(SqlFunction<Connection, T> operation) {
        return withConnection(connection -> {
            connection.setAutoCommit(false);
            var restoreAutoCommit = true;
            try {
                var result = operation.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | LinkageError error) {
                restoreAutoCommit = UsageSqliteTransactions.rollback(connection, error);
                throw error;
            } finally {
                if (restoreAutoCommit) {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    private <T> T withConnection(SqlFunction<Connection, T> operation) {
        boolean acquired = false;
        Connection connection = null;
        try {
            readerPermits.acquire();
            acquired = true;
            requireInitialized();
            synchronized (readerLifecycle) {
                connection = idleReaders.pollFirst();
            }
            if (connection == null) {
                connection = openConnection();
            }
            synchronized (readerLifecycle) {
                requireInitialized();
                activeReaders.add(connection);
            }
            return operation.apply(connection);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while waiting for a statistics reader", error);
        } catch (SQLException error) {
            throw databaseError("read statistics", error);
        } finally {
            releaseReader(connection);
            if (acquired) {
                readerPermits.release();
            }
        }
    }

    private void releaseReader(Connection connection) {
        if (connection == null) {
            return;
        }
        synchronized (readerLifecycle) {
            activeReaders.remove(connection);
            try {
                if (initialized && !closing.get() && !connection.isClosed()
                    && connection.getAutoCommit()) {
                    idleReaders.addLast(connection);
                    return;
                }
            } catch (SQLException ignored) {
                // A failed connection must not be reused by another query.
            }
        }
        close(connection);
    }

    private static int bindUsage(PreparedStatement statement, int index, NormalizedUsage usage)
        throws SQLException {
        var value = usage == null ? NormalizedUsage.missing() : usage;
        setLong(statement, index++, value.inputTokens());
        setLong(statement, index++, value.outputTokens());
        setLong(statement, index++, value.cacheReadInputTokens());
        setLong(statement, index++, value.cacheCreationInputTokens());
        setLong(statement, index++, value.reasoningOutputTokens());
        setLong(statement, index++, value.providerTotalTokens());
        setLong(statement, index++, value.accountedTotalTokens());
        statement.setString(index++, value.quality().name());
        return index;
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = nullableLong(row, column);
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        var value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static void setLong(PreparedStatement statement, int index, Long value)
        throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value)
        throws SQLException {
        setLong(statement, index, value == null ? null : value.toEpochMilli());
    }

    private static long longMeta(Connection connection, String key, long fallback) {
        try (var statement = connection.prepareStatement(
            "SELECT value FROM ai_statistics_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Long.parseLong(rows.getString(1)) : fallback;
            }
        } catch (SQLException error) {
            throw databaseError("read metadata", error);
        }
    }

    private static void putMeta(Connection connection, String key, String value)
        throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO ai_statistics_meta(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("AI usage statistics are not initialized");
        }
    }

    private void closeSilently() {
        var connection = writer;
        writer = null;
        close(connection);
    }

    private static synchronized void ensureDriverRegistered() throws SQLException {
        try {
            DriverManager.getDriver("jdbc:sqlite::memory:");
        } catch (SQLException ignored) {
            DriverManager.registerDriver(new JDBC());
        }
    }

    private static void deregisterPluginDrivers() {
        var pluginClassLoader = SqliteUsageStatisticsStore.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            var driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() != pluginClassLoader) {
                continue;
            }
            try {
                DriverManager.deregisterDriver(driver);
            } catch (SQLException error) {
                log.warn("Failed to deregister JDBC driver {}", driver.getClass().getName(), error);
            }
        }
    }

    private static void interrupt(Connection connection) {
        if (connection instanceof org.sqlite.SQLiteConnection sqlite) {
            try {
                sqlite.getDatabase().interrupt();
            } catch (SQLException error) {
                log.warn("Failed to interrupt an AI usage statistics connection");
            }
        }
    }

    private static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Best effort during shutdown.
            }
        }
    }

    private static void setAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException error) {
            log.warn("Failed to restore AI usage statistics auto-commit", error);
        }
    }

    private static IllegalStateException databaseError(String operation, SQLException error) {
        return new IllegalStateException("Failed to " + operation, error);
    }

    private static <T, R> R value(T source, java.util.function.Function<T, R> mapper) {
        return source == null ? null : mapper.apply(source);
    }

    @FunctionalInterface
    private interface SqlFunction<T, R> {
        R apply(T value) throws SQLException;
    }
}
