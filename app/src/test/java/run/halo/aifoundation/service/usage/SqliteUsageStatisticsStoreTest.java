package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteUsageStatisticsStoreTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteUsageStatisticsStore store;

    @AfterEach
    void close() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void persistsLogicalCallAndIdempotentPhysicalAttempt() {
        store = createStore();
        var start = start("call-1", Instant.parse("2026-08-10T10:00:00Z"));
        var usage = usage(10, 5);
        store.startCall(start);
        var execution = new UsageExecutionRecord("execution-1", start.id(), start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 0, start.startedAt(),
            start.startedAt().plusMillis(50), UsageStatus.SUCCEEDED, null, start.requestModelId(),
            "actual-model", usage);
        store.recordExecution(execution);
        store.recordExecution(execution);
        store.finishCall(terminal(start, usage));

        var detail = store.getCall(start.id()).orElseThrow();
        assertThat(detail.executions()).hasSize(1);
        assertThat(detail.call().usage().accountedTotalTokens()).isEqualTo(15L);
        assertThat(detail.call().status()).isEqualTo(UsageStatus.SUCCEEDED);
        var summary = store.summary(query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"),
            true);
        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isEqualTo(15L);
        assertThat(summary.usageCoverage()).isEqualTo(1D);
        assertThat(quickCheck(paths().database())).isEqualTo("ok");
    }

    @Test
    void summaryAccountsForInProgressCallsInStatusBreakdown() {
        store = createStore();
        var start = start("in-progress", Instant.parse("2026-08-10T10:00:00Z"));
        store.startCall(start);

        var summary = store.summary(query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"),
            true);

        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.inProgressCount()).isEqualTo(1);
        assertThat(summary.successCount() + summary.failedCount() + summary.timedOutCount()
            + summary.cancelledCount() + summary.abandonedCount()
            + summary.inProgressCount()).isEqualTo(summary.callCount());
    }

    @Test
    void aggregateReadsUseOneSnapshotDuringConcurrentWrite() throws Exception {
        var queries = new SnapshotBarrierQueryRepository();
        store = new SqliteUsageStatisticsStore(
            new UsageDatabasePaths(temporaryDirectory.resolve("plugins")),
            new UsageStatisticsMaintenance(), queries);
        store.initialize();
        store.startCall(start("before-snapshot", Instant.parse("2026-08-10T10:00:00Z")));
        var range = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");

        var summary = CompletableFuture.supplyAsync(() -> store.summary(range, true));
        assertThat(queries.snapshotStarted.await(5, TimeUnit.SECONDS)).isTrue();
        store.startCall(start("after-snapshot", Instant.parse("2026-08-10T11:00:00Z")));
        queries.writeCompleted.countDown();

        assertThat(summary.get(5, TimeUnit.SECONDS).callCount()).isEqualTo(1);
        assertThat(store.summary(range, true).callCount()).isEqualTo(2);
    }

    @Test
    void tokenStatusFilterUsesExecutionOutcomeAcrossSuccessfulRetry() {
        store = createStore();
        var start = start("retried", Instant.parse("2026-08-10T10:00:00Z"));
        store.startCall(start);
        store.recordExecution(new UsageExecutionRecord("failed-attempt", start.id(), start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 0, start.startedAt(),
            start.startedAt().plusMillis(10), UsageStatus.FAILED, null, start.requestModelId(),
            null, usage(7, 3)));
        store.recordExecution(new UsageExecutionRecord("successful-attempt", start.id(),
            start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 1, start.startedAt().plusMillis(20),
            start.startedAt().plusMillis(30), UsageStatus.SUCCEEDED, null, start.requestModelId(),
            "actual", usage(4, 1)));
        store.finishCall(terminal(start, usage(11, 4)));
        var base = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");

        assertThat(store.summary(base, true).accountedTotalTokens()).isEqualTo(15L);
        assertThat(store.summary(withStatus(base, UsageStatus.SUCCEEDED), true)
            .accountedTotalTokens()).isEqualTo(5L);
        var failed = store.summary(withStatus(base, UsageStatus.FAILED), true);
        assertThat(failed.accountedTotalTokens()).isEqualTo(10L);
        assertThat(failed.callCount()).isZero();

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(withStatus(base, UsageStatus.FAILED), true)
            .accountedTotalTokens()).isEqualTo(10L);
    }

    @Test
    void doesNotRewriteArchivedTokenFactsAfterExecutionRetention() {
        store = createStore();
        var start = start("archived-retry", Instant.parse("2026-07-01T10:00:00Z"));
        store.startCall(start);
        store.recordExecution(new UsageExecutionRecord("archived-failed", start.id(), start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 0, start.startedAt(),
            start.startedAt().plusMillis(10), UsageStatus.FAILED, null, start.requestModelId(),
            null, usage(7, 3)));
        store.recordExecution(new UsageExecutionRecord("archived-success", start.id(),
            start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 1, start.startedAt().plusMillis(20),
            start.startedAt().plusMillis(30), UsageStatus.SUCCEEDED, null, start.requestModelId(),
            "actual", usage(4, 1)));
        store.finishCall(terminal(start, usage(11, 4)));
        var failedQuery = withStatus(
            query("2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z"), UsageStatus.FAILED);

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(failedQuery, true).accountedTotalTokens()).isEqualTo(10L);

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(failedQuery, true).accountedTotalTokens()).isEqualTo(10L);
    }

    @Test
    void retainsUsageFromExecutionThatStartedOnDayAfterLogicalCall() {
        store = createStore();
        var start = start("cross-day", Instant.parse("2026-07-01T23:59:59Z"));
        store.startCall(start);
        var executionStart = Instant.parse("2026-07-02T00:00:01Z");
        store.recordExecution(new UsageExecutionRecord("cross-day-execution", start.id(),
            start.epoch(), UsageUnitKind.GENERATION_STEP, 0, 0, executionStart,
            executionStart.plusMillis(10), UsageStatus.SUCCEEDED, null,
            start.requestModelId(), "actual", usage(10, 5)));
        store.finishCall(new UsageCallTerminal(start, executionStart.plusMillis(10),
            UsageStatus.SUCCEEDED, null, "actual", 1, 1, 0, true, usage(10, 5)));
        var executionDay = query("2026-07-02T00:00:00Z", "2026-07-03T00:00:00Z");

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-02T02:00:00Z"), ZoneOffset.UTC));

        var summary = store.summary(executionDay, true);
        assertThat(summary.callCount()).isZero();
        assertThat(summary.accountedTotalTokens()).isEqualTo(15L);
        assertThat(summary.resolution()).isEqualTo("DAY");

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-03T02:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(executionDay, true).accountedTotalTokens()).isEqualTo(15L);
    }

    @Test
    void rerollsRecentDaysSoLateTerminalUpdatesAreNotFrozen() {
        store = createStore();
        var start = start("late-terminal", Instant.parse("2026-08-10T23:59:00Z"));
        store.startCall(start);
        var query = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(query, true).inProgressCount()).isEqualTo(1);

        store.finishCall(terminal(start, usage(4, 2)));
        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneOffset.UTC));

        var summary = store.summary(query, true);
        assertThat(summary.inProgressCount()).isZero();
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isEqualTo(6L);
    }

    @Test
    void rerollsCallFactsUntilCallDetailRetentionExpires() {
        store = createStore();
        var start = start("long-running-call", Instant.parse("2026-07-01T10:00:00Z"));
        store.startCall(start);
        var query = query("2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z");

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(query, true).inProgressCount()).isEqualTo(1);

        store.finishCall(terminal(start, usage(4, 2)));
        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneOffset.UTC));

        var summary = store.summary(query, true);
        assertThat(summary.inProgressCount()).isZero();
        assertThat(summary.successCount()).isEqualTo(1);
    }

    @Test
    void rollupWaitsForTheSafetyDelayAfterUtcMidnight() {
        store = createStore();
        var start = start("safety-delay", Instant.parse("2026-08-10T23:59:00Z"));
        store.finishCall(terminal(start, usage(1, 1)));
        var query = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");

        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-11T00:30:00Z"), ZoneOffset.UTC));

        assertThat(store.summary(query, true).resolution()).isEqualTo("MILLISECOND");
    }

    @Test
    void rollsUpWithoutDoubleCountingAndRetainsDailyHistory() {
        store = createStore();
        var start = start("old-call", Instant.parse("2026-04-01T10:00:00Z"));
        store.finishCall(terminal(start, usage(7, 3)));

        store.rollupAndRetain(Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));

        assertThat(store.getCall(start.id())).isEmpty();
        var summary = store.summary(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"),
            true);
        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isEqualTo(10L);
        assertThat(summary.resolution()).isEqualTo("DAY");
        assertThat(store.trends(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"), true))
            .singleElement().extracting(UsageTrendPoint::callCount).isEqualTo(1L);
    }

    @Test
    void hourlyTrendsUseExecutionFactsAndArchivedIntervalsRemainDaily() {
        store = createStore();
        var start = start("hourly", Instant.parse("2026-08-10T10:15:00Z"));
        store.startCall(start);
        store.recordExecution(new UsageExecutionRecord("hourly-failed", start.id(), start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 0, start.startedAt(),
            start.startedAt().plusMillis(10), UsageStatus.FAILED, null, start.requestModelId(),
            null, usage(7, 3)));
        var successfulAttemptStartedAt = start.startedAt()
            .plus(java.time.Duration.ofMinutes(1));
        store.recordExecution(new UsageExecutionRecord("hourly-success", start.id(), start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 1, successfulAttemptStartedAt,
            successfulAttemptStartedAt.plusMillis(10), UsageStatus.SUCCEEDED, null,
            start.requestModelId(), "actual", usage(5, 5)));
        store.finishCall(new UsageCallTerminal(start,
            start.startedAt().plus(java.time.Duration.ofMinutes(2)),
            UsageStatus.SUCCEEDED, null, "actual", 1, 2, 0, true, usage(12, 8)));
        var hourlyQuery = new UsageQuery(Instant.parse("2026-08-10T00:00:00Z"),
            Instant.parse("2026-08-11T00:00:00Z"), null, null, null, null, null, null,
            null, null, UsageTrendResolution.HOUR);

        var points = store.trends(hourlyQuery, true);

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().bucketStart())
            .isEqualTo(Instant.parse("2026-08-10T10:00:00Z"));
        assertThat(points.getFirst().resolution()).isEqualTo(UsageTrendResolution.HOUR);
        assertThat(points.getFirst().callCount()).isEqualTo(1);
        assertThat(points.getFirst().inputTokens()).isEqualTo(12L);
        assertThat(points.getFirst().accountedTotalTokens()).isEqualTo(20L);
        assertThat(store.trends(hourlyQuery, false)).allMatch(point -> !point.complete());

        var old = start("archived-hourly", Instant.parse("2026-04-01T10:00:00Z"));
        store.finishCall(terminal(old, usage(1, 1)));
        store.rollupAndRetain(Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"),
            ZoneOffset.UTC));
        assertThat(store.trends(hourlyQuery, true)).singleElement()
            .extracting(UsageTrendPoint::resolution).isEqualTo(UsageTrendResolution.HOUR);
        var archivedQuery = new UsageQuery(Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-04-02T00:00:00Z"), null, null, null, null, null, null,
            null, null, UsageTrendResolution.HOUR);

        assertThat(store.trends(archivedQuery, true)).singleElement()
            .extracting(UsageTrendPoint::resolution).isEqualTo(UsageTrendResolution.DAY);
    }

    @Test
    void trendsDiscloseIncompleteBuckets() {
        store = createStore();
        var start = start("incomplete-trend", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(new UsageCallTerminal(start, start.startedAt().plusMillis(100),
            UsageStatus.SUCCEEDED, null, "actual-model", 1, 1, 1, false, usage(2, 1)));

        var points = store.trends(
            query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"), true);

        assertThat(points).singleElement().extracting(UsageTrendPoint::complete).isEqualTo(false);
    }

    @Test
    void resetRejectsLateEventsFromPreviousEpoch() {
        store = createStore();
        var start = start("late-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.startCall(start);
        assertThat(store.reset()).isEqualTo(2);

        store.finishCall(terminal(start, usage(1, 1)));

        assertThat(store.getCall(start.id())).isEmpty();
    }

    @Test
    void persistsHealthAcrossStoreRestartAndClearsItOnReset() {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var affected = Instant.parse("2026-08-10T10:00:00Z");
        var lastError = Instant.parse("2026-08-10T10:01:00Z");
        store.writeHealth(new UsageHealthState(3, 2, 1, lastError, affected, lastError,
            null, null));
        store.close();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        assertThat(store.readHealth()).isEqualTo(
            new UsageHealthState(3, 2, 1, lastError, affected, lastError, null, null));

        store.reset();
        assertThat(store.readHealth()).isEqualTo(UsageHealthState.empty());
    }

    @Test
    void createsConsistentSnapshotAndRestoresWhenRuntimeDatabaseIsMissing() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var start = start("backup-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(start, usage(2, 2)));
        store.backup();
        store.backup();
        store.backup();
        assertThat(UsageSqliteFiles.listBackups(paths)).hasSize(2);
        store.close();
        store = null;
        Files.delete(paths.database());

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        assertThat(store.getCall(start.id())).isPresent();
        assertThat(quickCheck(paths.database())).isEqualTo("ok");
        assertThat(store.readHealth().integrityError())
            .isEqualTo("RECOVERED_FROM_SNAPSHOT");
        assertThat(store.readHealth().affectedSince()).isNotNull();
        assertThat(store.readHealth().affectedUntil()).isNotNull();
    }

    @Test
    void restoresValidatedBackupAndPreservesInvalidLiveDatabase() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var start = start("recovered-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(start, usage(2, 2)));
        store.backup();
        store.close();
        store = null;
        Files.writeString(paths.database(), "invalid-live-database");

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        assertThat(store.getCall(start.id())).isPresent();
        assertThat(store.readHealth().integrityError())
            .isEqualTo("RECOVERED_FROM_SNAPSHOT");
        assertThat(store.readHealth().affectedSince()).isBefore(
            store.readHealth().affectedUntil());
        try (var files = Files.list(paths.backupDirectory().resolve("corrupted"))) {
            var evidence = files.filter(Files::isRegularFile).toList();
            assertThat(evidence).hasSize(1);
            assertThat(Files.readString(evidence.getFirst())).isEqualTo("invalid-live-database");
        }
    }

    @Test
    void restoresBackupWhenCurrentSchemaMarkerExistsButRequiredTableIsMissing() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var start = start("schema-recovered-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(start, usage(2, 2)));
        store.backup();
        store.close();
        store = null;
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            connection.createStatement().execute("DROP TABLE ai_model_executions");
        }

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        assertThat(store.getCall(start.id())).isPresent();
        assertThat(quickCheck(paths.database())).isEqualTo("ok");
        try (var files = Files.list(paths.backupDirectory().resolve("corrupted"))) {
            assertThat(files.filter(Files::isRegularFile).toList()).hasSize(1);
        }
    }

    @Test
    void preservesIncompleteCurrentSchemaAndDoesNotCreateEmptyReplacement() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.close();
        store = null;
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            connection.createStatement().execute("DROP TABLE ai_model_executions");
        }

        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize).isInstanceOf(IllegalStateException.class);
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var rows = connection.createStatement().executeQuery(
                "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("4");
        }
        try (var files = Files.list(paths.backupDirectory().resolve("corrupted"))) {
            assertThat(files.filter(Files::isRegularFile).toList()).hasSize(1);
        }
    }

    @Test
    void cursorIsBoundToFiltersAndPersistedSchemaContainsNoPayloadColumns() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var first = start("call-a", Instant.parse("2026-08-10T11:00:00Z"));
        var second = start("call-b", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(first, usage(1, 1)));
        store.finishCall(terminal(second, usage(1, 1)));
        var query = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");
        var page = store.listCalls(query, 1, null);
        assertThat(page.nextCursor()).isNotBlank();
        var differentFilter = new UsageQuery(query.from(), query.to(), "another-plugin", null,
            null, null, null, null, null, null, null);
        assertThatThrownBy(() -> store.listCalls(differentFilter, 1, page.nextCursor()))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid cursor");

        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var rows = connection.createStatement().executeQuery("PRAGMA table_info(ai_calls)")) {
            var columns = new java.util.ArrayList<String>();
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
            assertThat(columns).doesNotContain("prompt", "output", "messages", "tools",
                "headers", "provider_body", "raw_usage", "request_metadata");
        }
    }

    @Test
    void corruptDatabaseIsPreservedAndNotSilentlyReplaced() throws Exception {
        var paths = paths();
        Files.createDirectories(paths.database().getParent());
        var corrupt = "not-a-sqlite-database".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(paths.database(), corrupt);
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readAllBytes(paths.database())).isEqualTo(corrupt);
    }

    @Test
    void zeroByteDatabaseIsEvidenceAndIsNotSilentlyReplaced() throws Exception {
        var paths = paths();
        Files.createDirectories(paths.database().getParent());
        Files.createFile(paths.database());
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(Files.size(paths.database())).isZero();
        try (var files = Files.list(paths.backupDirectory().resolve("corrupted"))) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void orphanedWalIsEvidenceAndIsNotSilentlyDiscarded() throws Exception {
        var paths = paths();
        Files.createDirectories(paths.database().getParent());
        var wal = paths.database().resolveSibling(paths.database().getFileName() + "-wal");
        Files.writeString(wal, "orphaned-wal-evidence");
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(wal)).isEqualTo("orphaned-wal-evidence");
        try (var files = Files.list(paths.backupDirectory().resolve("corrupted"))) {
            assertThat(files.map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.endsWith("-wal"));
        }
    }

    @Test
    void connectionsCanBeOpenedAndClosedAcrossRepeatedPluginLifecycles() {
        var paths = paths();
        for (int i = 0; i < 3; i++) {
            store = new SqliteUsageStatisticsStore(paths);
            store.initialize();
            assertThat(quickCheck(paths.database())).isEqualTo("ok");
            store.close();
            store = null;
        }
    }

    @Test
    void reconcilesInProgressCallsAsAbandonedAfterAbruptStop() {
        var paths = paths();
        var start = start("interrupted", Instant.parse("2026-08-10T10:00:00Z"));
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.startCall(start);
        store.close();

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.reconcileAbandoned(Instant.parse("2026-08-10T10:05:00Z"));

        var call = store.getCall(start.id()).orElseThrow().call();
        assertThat(call.status()).isEqualTo(UsageStatus.ABANDONED);
        assertThat(call.complete()).isFalse();
    }

    @Test
    void upgradesVersionOneTransactionallyAfterCreatingConsistentBackup() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.close();
        store = null;
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var statement = connection.createStatement()) {
            statement.execute("DROP TABLE ai_statistics_health");
            statement.execute("DROP TABLE ai_token_usage_daily");
            statement.execute("DROP INDEX idx_executions_started");
            statement.execute("UPDATE ai_statistics_meta SET value = '1'"
                + " WHERE key = 'schema_version'");
        }

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        assertThat(Files.isRegularFile(paths.migrationBackup())).isTrue();
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var rows = connection.createStatement().executeQuery(
                "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("4");
        }
    }

    @Test
    void failedUpgradeRollsBackAndPreservesVersionOneSource() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.close();
        store = null;
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var statement = connection.createStatement()) {
            statement.execute("DROP TABLE ai_statistics_health");
            statement.execute("DROP TABLE ai_token_usage_daily");
            statement.execute("DROP INDEX idx_executions_started");
            statement.execute("CREATE VIEW ai_statistics_health AS SELECT 1 AS id");
            statement.execute("UPDATE ai_statistics_meta SET value = '1'"
                + " WHERE key = 'schema_version'");
        }
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize).isInstanceOf(IllegalStateException.class);
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var rows = connection.createStatement().executeQuery(
                "SELECT value FROM ai_statistics_meta WHERE key = 'schema_version'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("1");
        }
        assertThat(Files.isRegularFile(paths.migrationBackup())).isTrue();
    }

    @Test
    void rollupFailureIsAtomicAndRetryDoesNotDoubleCount() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var start = start("rollup-retry", Instant.parse("2026-04-01T10:00:00Z"));
        store.finishCall(terminal(start, usage(5, 5)));
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            connection.createStatement().execute("""
                CREATE TRIGGER fail_daily BEFORE INSERT ON ai_usage_daily
                BEGIN SELECT RAISE(ABORT, 'injected'); END
                """);
        }
        var clock = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> store.rollupAndRetain(clock))
            .isInstanceOf(IllegalStateException.class);
        assertThat(store.getCall(start.id())).isPresent();
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            connection.createStatement().execute("DROP TRIGGER fail_daily");
        }

        store.rollupAndRetain(clock);
        store.rollupAndRetain(clock);

        var fullDay = store.summary(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"),
            true);
        assertThat(fullDay.callCount()).isEqualTo(1);
        assertThat(fullDay.accountedTotalTokens()).isEqualTo(10L);
        var partialDay = store.summary(query("2026-04-01T12:00:00Z", "2026-04-02T00:00:00Z"),
            true);
        assertThat(partialDay.callCount()).isZero();
    }

    private SqliteUsageStatisticsStore createStore() {
        var result = new SqliteUsageStatisticsStore(paths());
        result.initialize();
        return result;
    }

    private UsageDatabasePaths paths() {
        return new UsageDatabasePaths(temporaryDirectory.resolve("plugins"));
    }

    private static UsageCallStart start(String id, Instant startedAt) {
        return new UsageCallStart(id, 1, startedAt, "plugin-search", "1.2.3", "stack",
            "semantic-search", "language.generateText", "LANGUAGE", "configured-model",
            "provider-resource", "openai", "requested-model", false);
    }

    private static UsageCallTerminal terminal(UsageCallStart start, NormalizedUsage usage) {
        return new UsageCallTerminal(start, start.startedAt().plusMillis(100),
            UsageStatus.SUCCEEDED, null, "actual-model", 1, 1, 0, true, usage);
    }

    private static NormalizedUsage usage(long input, long output) {
        return new NormalizedUsage(input, output, null, null, null, null, null, null);
    }

    private static UsageQuery query(String from, String to) {
        return new UsageQuery(Instant.parse(from), Instant.parse(to), null, null, null, null,
            null, null, null, null, null);
    }

    private static UsageQuery withStatus(UsageQuery query, UsageStatus status) {
        return new UsageQuery(query.from(), query.to(), query.callerPlugin(), query.feature(),
            query.providerName(), query.modelName(), query.modelType(), query.operation(), status,
            query.usageQuality(), query.resolution());
    }

    private static String quickCheck(Path database) {
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + database, new java.util.Properties());
            var rows = connection.createStatement().executeQuery("PRAGMA quick_check")) {
            return rows.next() ? rows.getString(1) : "no-result";
        } catch (SQLException error) {
            throw new IllegalStateException("Failed to check test database", error);
        }
    }

    private static final class SnapshotBarrierQueryRepository
        extends UsageStatisticsQueryRepository {

        private final CountDownLatch snapshotStarted = new CountDownLatch(1);
        private final CountDownLatch writeCompleted = new CountDownLatch(1);

        @Override
        UsageSummary summary(Connection connection, UsageQuery query, boolean complete)
            throws SQLException {
            try (var statement = connection.createStatement();
                var ignored = statement.executeQuery("SELECT COUNT(*) FROM ai_calls")) {
                ignored.next();
            }
            snapshotStarted.countDown();
            try {
                if (!writeCompleted.await(5, TimeUnit.SECONDS)) {
                    throw new SQLException("Timed out waiting for concurrent write");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for concurrent write", error);
            }
            return super.summary(connection, query, complete);
        }
    }
}
