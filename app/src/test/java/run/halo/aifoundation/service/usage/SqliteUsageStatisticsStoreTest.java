package run.halo.aifoundation.service.usage;

import static run.halo.aifoundation.service.usage.UsageStatisticsTestSupport.retain;
import static run.halo.aifoundation.service.usage.UsageStatisticsTestSupport.storeWithQueries;

import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageUnitKind;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void boundsExecutionDeletionAndRetainsCallFacts() throws Exception {
        store = createStore();
        var call = start("many-attempts", Instant.parse("2026-04-01T10:00:00Z"));
        store.startCall(call);
        var count = UsageStatisticsMaintenance.EXECUTION_BATCH_SIZE * 3 + 1;
        store.writeBatch(java.util.List.of(() -> {
            for (int i = 0; i < count; i++) {
                store.recordExecution(new UsageExecutionRecord("execution-" + i, call.id(), 1,
                    UsageUnitKind.GENERATION_STEP, 0, i, call.startedAt(), call.startedAt(),
                    UsageStatus.SUCCEEDED, null, "requested", "actual", usage(1, 1)));
            }
            store.finishCall(terminal(call, usage(count, count)));
        }));
        var clock = Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC);
        assertThat(store.retainBatch(clock)).isTrue();
        assertThat(store.getCall(call.id())).isPresent();
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + paths().database());
            var statement = connection.createStatement();
            var rows = statement.executeQuery("SELECT COUNT(*) FROM ai_model_executions")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(UsageStatisticsMaintenance.EXECUTION_BATCH_SIZE * 2 + 1);
        }
        retain(store, clock);
        assertThat(store.getCall(call.id()).orElseThrow().executions()).isEmpty();
        assertThat(store.summary(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"), true)
            .accountedTotalTokens()).isEqualTo(count * 2L);
    }

    @Test
    void partialRetentionBatchesKeepTotalsAndCanResumeWithoutDoubleCounting() {
        store = new SqliteUsageStatisticsStore(paths());
        store.initialize();
        var at = Instant.parse("2026-04-01T10:00:00Z");
        var count = 257;
        for (int i = 0; i < count; i++) {
            var call = start("batch-" + i, at);
            store.startCall(call);
            store.finishCall(new UsageCallTerminal(call, at.plusSeconds(1), UsageStatus.SUCCEEDED,
                null, "actual", 1, 1, 0, true,
                new NormalizedUsage(2L, 1L, null, null, null, null, null, null)));
        }
        var clock = Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC);
        var query = query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z");
        assertThat(store.retainBatch(clock)).isFalse();
        assertThat(store.summary(query, true).callCount()).isEqualTo(count);
        store.close();
        store = new SqliteUsageStatisticsStore(paths());
        store.initialize();
        retain(store, clock);
        retain(store, clock);
        assertThat(store.summary(query, true).callCount()).isEqualTo(count);
        assertThat(store.summary(query, true).accountedTotalTokens()).isEqualTo(count * 3L);
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
        assertThat(summary.successCount() + summary.failedCount()
            + summary.inProgressCount()).isEqualTo(summary.callCount());
    }

    @Test
    void aggregateReadsUseOneSnapshotDuringConcurrentWrite() throws Exception {
        var queries = new SnapshotBarrierQueryRepository();
        store = storeWithQueries(new UsageDatabasePaths(temporaryDirectory.resolve("plugins")),
            queries);
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
    void tokenStatusFilterUsesLogicalOutcomeIncludingAllRetryUsage() {
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
            .accountedTotalTokens()).isEqualTo(15L);
        var failed = store.summary(withStatus(base, UsageStatus.FAILED), true);
        assertThat(failed.accountedTotalTokens()).isNull();
        assertThat(failed.callCount()).isZero();

        retain(store,
            Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(withStatus(base, UsageStatus.FAILED), true)
            .accountedTotalTokens()).isNull();
    }

    @Test
    void doesNotRewriteTokenFactsAfterExecutionRetention() {
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
            query("2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z"), UsageStatus.SUCCEEDED);

        retain(store,
            Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(failedQuery, true).accountedTotalTokens()).isEqualTo(15L);

        retain(store,
            Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(failedQuery, true).accountedTotalTokens()).isEqualTo(15L);
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
        var executionDay = query("2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z");

        retain(store,
            Clock.fixed(Instant.parse("2026-08-02T02:00:00Z"), ZoneOffset.UTC));

        var summary = store.summary(executionDay, true);
        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isEqualTo(15L);

        retain(store,
            Clock.fixed(Instant.parse("2026-08-03T02:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(executionDay, true).accountedTotalTokens()).isEqualTo(15L);
    }

    @Test
    void lateTerminalUpdatesRemainVisibleAfterMaintenance() {
        store = createStore();
        var start = start("late-terminal", Instant.parse("2026-08-10T23:59:00Z"));
        store.startCall(start);
        var query = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");

        retain(store,
            Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(query, true).inProgressCount()).isEqualTo(1);

        store.finishCall(terminal(start, usage(4, 2)));
        retain(store,
            Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneOffset.UTC));

        var summary = store.summary(query, true);
        assertThat(summary.inProgressCount()).isZero();
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isEqualTo(6L);
    }

    @Test
    void longRunningCallsRemainUpdatableAfterMaintenance() {
        store = createStore();
        var start = start("long-running-call", Instant.parse("2026-07-01T10:00:00Z"));
        store.startCall(start);
        var query = query("2026-07-01T00:00:00Z", "2026-07-02T00:00:00Z");

        retain(store,
            Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"), ZoneOffset.UTC));
        assertThat(store.summary(query, true).inProgressCount()).isEqualTo(1);

        store.finishCall(terminal(start, usage(4, 2)));
        retain(store,
            Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneOffset.UTC));

        var summary = store.summary(query, true);
        assertThat(summary.inProgressCount()).isZero();
        assertThat(summary.successCount()).isEqualTo(1);
    }

    @Test
    void retentionLeavesCallFactsInPlace() {
        store = createStore();
        var start = start("safety-delay", Instant.parse("2026-08-10T23:59:00Z"));
        store.finishCall(terminal(start, usage(1, 1)));
        var query = query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z");

        retain(store,
            Clock.fixed(Instant.parse("2026-08-11T00:30:00Z"), ZoneOffset.UTC));

        assertThat(store.getCall(start.id())).isPresent();
        assertThat(store.summary(query, true).callCount()).isEqualTo(1);
    }

    @Test
    void retainsCallFactsWithoutDoubleCounting() {
        store = createStore();
        var start = start("old-call", Instant.parse("2026-04-01T10:00:00Z"));
        store.finishCall(terminal(start, usage(7, 3)));

        retain(store, Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));

        assertThat(store.getCall(start.id())).isPresent();
        var summary = store.summary(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"),
            true);
        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isEqualTo(10L);
        assertThat(store.trends(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"), true, null))
            .singleElement().extracting(UsageTrendPoint::callCount).isEqualTo(1L);
    }

    @Test
    void hourlyTrendsKeepTheirResolutionAfterExecutionRetention() {
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

        var points = store.trends(hourlyQuery, true, null);

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().bucketStart())
            .isEqualTo(Instant.parse("2026-08-10T10:00:00Z"));
        assertThat(points.getFirst().resolution()).isEqualTo(UsageTrendResolution.HOUR);
        assertThat(points.getFirst().callCount()).isEqualTo(1);
        assertThat(points.getFirst().inputTokens()).isEqualTo(12L);
        assertThat(points.getFirst().accountedTotalTokens()).isEqualTo(20L);
        assertThat(store.trends(hourlyQuery, false, null)).allMatch(point -> !point.complete());

        var old = start("archived-hourly", Instant.parse("2026-04-01T10:00:00Z"));
        store.finishCall(terminal(old, usage(1, 1)));
        retain(store, Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"),
            ZoneOffset.UTC));
        assertThat(store.trends(hourlyQuery, true, null)).singleElement()
            .extracting(UsageTrendPoint::resolution).isEqualTo(UsageTrendResolution.HOUR);
        var archivedQuery = new UsageQuery(Instant.parse("2026-04-01T00:00:00Z"),
            Instant.parse("2026-04-02T00:00:00Z"), null, null, null, null, null, null,
            null, null, UsageTrendResolution.HOUR);

        assertThat(store.trends(archivedQuery, true, null)).singleElement()
            .extracting(UsageTrendPoint::resolution).isEqualTo(UsageTrendResolution.HOUR);
    }

    @Test
    void trendsDiscloseIncompleteBuckets() {
        store = createStore();
        var start = start("incomplete-trend", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(new UsageCallTerminal(start, start.startedAt().plusMillis(100),
            UsageStatus.SUCCEEDED, null, "actual-model", 1, 1, 1, false, usage(2, 1)));

        var points = store.trends(
            query("2026-08-10T00:00:00Z", "2026-08-11T00:00:00Z"), true, null);

        assertThat(points).singleElement().extracting(UsageTrendPoint::complete).isEqualTo(false);
    }

    @Test
    void breakdownGroupsAcrossHistoricalAndRecentCalls() {
        store = createStore();
        var old = new UsageCallStart("breakdown-old", 1, Instant.parse("2026-04-01T10:00:00Z"),
            "plugin-search", "1.0.0", "stack", "semantic-search", "language.generateText",
            "LANGUAGE", "model-a", "provider-a", "openai", "requested", false);
        store.finishCall(terminal(old, usage(10, 5)));
        retain(store, Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"),
            ZoneOffset.UTC));

        var recent = new UsageCallStart("breakdown-recent", 1,
            Instant.parse("2026-08-10T10:00:00Z"), "plugin-search", "1.0.0", "stack",
            "semantic-search", "language.generateText", "LANGUAGE", "model-b", "provider-a",
            "openai", "requested", false);
        store.finishCall(terminal(recent, usage(2, 1)));
        var failed = new UsageCallStart("breakdown-failed", 1,
            Instant.parse("2026-08-10T11:00:00Z"), "plugin-search", "1.0.0", "stack",
            "semantic-search", "language.generateText", "LANGUAGE", "model-b", "provider-a",
            "openai", "requested", false);
        store.finishCall(new UsageCallTerminal(failed, failed.startedAt().plusMillis(100),
            UsageStatus.FAILED, null, null, 1, 1, 0, true, usage(1, 1)));

        var breakdown = store.breakdown(
            query("2026-04-01T00:00:00Z", "2026-08-11T00:00:00Z"),
            UsageBreakdownDimension.MODEL);

        assertThat(breakdown.items()).extracting(UsageBreakdownItem::value)
            .containsExactly("model-a", "model-b");
        assertThat(breakdown.items().get(0).callCount()).isEqualTo(1);
        assertThat(breakdown.items().get(0).accountedTotalTokens()).isEqualTo(15L);
        assertThat(breakdown.items().get(1).callCount()).isEqualTo(2);
        assertThat(breakdown.items().get(1).failedCount()).isEqualTo(1);
        assertThat(breakdown.items().get(1).accountedTotalTokens()).isEqualTo(5L);

        var filtered = store.breakdown(new UsageQuery(Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-08-11T00:00:00Z"), null, null, null, "model-a", null, null,
                null, null, null),
            UsageBreakdownDimension.PROVIDER);
        assertThat(filtered.items()).singleElement().satisfies(item -> {
            assertThat(item.value()).isEqualTo("provider-a");
            assertThat(item.accountedTotalTokens()).isEqualTo(15L);
        });
    }

    @Test
    void trendsSplitHistoricalAndRecentBucketsByDimension() {
        store = createStore();
        var old = new UsageCallStart("trend-dim-old", 1, Instant.parse("2026-04-01T10:00:00Z"),
            "plugin-search", "1.0.0", "stack", "semantic-search", "language.generateText",
            "LANGUAGE", "model-a", "provider-a", "openai", "requested", false);
        store.finishCall(terminal(old, usage(10, 5)));
        retain(store, Clock.fixed(Instant.parse("2026-08-11T02:00:00Z"),
            ZoneOffset.UTC));
        var recent = new UsageCallStart("trend-dim-recent", 1,
            Instant.parse("2026-08-10T10:00:00Z"), "plugin-search", "1.0.0", "stack",
            "semantic-search", "language.generateText", "LANGUAGE", "model-b", "provider-a",
            "openai", "requested", false);
        store.finishCall(terminal(recent, usage(2, 1)));

        var points = store.trends(query("2026-04-01T00:00:00Z", "2026-08-11T00:00:00Z"), true,
            UsageBreakdownDimension.MODEL);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).byDimension()).containsOnlyKeys("model-a");
        assertThat(points.get(0).byDimension().get("model-a").accountedTotalTokens())
            .isEqualTo(15L);
        assertThat(points.get(1).byDimension()).containsOnlyKeys("model-b");
        assertThat(points.get(1).byDimension().get("model-b").callCount()).isEqualTo(1);
        assertThat(points.get(1).byDimension().get("model-b").accountedTotalTokens())
            .isEqualTo(3L);

        assertThat(store.trends(query("2026-04-01T00:00:00Z", "2026-08-11T00:00:00Z"), true, null))
            .allMatch(point -> point.byDimension() == null);
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
        assertThat(store.readHealth()).isEqualTo(
            new UsageHealthState(0, 0, 0, null, null, null, null, null));
    }

    @Test
    void missingTableIsNotDetectedWhileSchemaMarkerIsCurrent() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var start = start("schema-recovered-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(start, usage(2, 2)));
        store.close();
        store = null;
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            connection.createStatement().execute("DROP TABLE ai_model_executions");
        }

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        // Schema validation trusts the schema_version marker; the damage surfaces on reads.
        assertThatThrownBy(() -> store.getCall(start.id()))
            .isInstanceOf(IllegalStateException.class);
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            assertThat(UsageSqliteJdbc.longMeta(connection, "schema_version", 0)).isEqualTo(1);
        }
    }

    @Test
    void missingTokenColumnIsNotDetectedWhileSchemaMarkerIsCurrent() throws Exception {
        var paths = paths();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var start = start("schema-recovered-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(start, usage(2, 2)));
        store.close();
        store = null;
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties())) {
            connection.createStatement().execute("DROP INDEX idx_calls_filtered_hour");
            connection.createStatement().execute("ALTER TABLE ai_calls DROP COLUMN input_tokens");
        }

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();

        // Schema validation trusts the schema_version marker; the damage surfaces on reads.
        assertThatThrownBy(() -> store.getCall(start.id()))
            .isInstanceOf(IllegalStateException.class);
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

        assertThatThrownBy(store::initialize)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(paths.database().toString())
            .hasCauseInstanceOf(SQLException.class);
        store.close();
        assertThat(Files.readAllBytes(paths.database())).isEqualTo(corrupt);
        try (var files = Files.list(paths.database().getParent())) {
            assertThat(files).containsExactly(paths.database());
        }
    }

    @Test
    void zeroByteDatabaseIsEvidenceAndIsNotSilentlyReplaced() throws Exception {
        var paths = paths();
        Files.createDirectories(paths.database().getParent());
        Files.createFile(paths.database());
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize)
            .hasCauseInstanceOf(UsageDatabaseIntegrityException.class);
        store.close();
        assertThat(Files.size(paths.database())).isZero();
        try (var files = Files.list(paths.database().getParent())) {
            assertThat(files).containsExactly(paths.database());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-wal", "-shm"})
    void orphanedSidecarIsPreservedWithoutCreatingDatabase(String suffix) throws Exception {
        var paths = paths();
        Files.createDirectories(paths.database().getParent());
        var sidecar = paths.database().resolveSibling(paths.database().getFileName() + suffix);
        Files.writeString(sidecar, "orphaned-sidecar-evidence");
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize)
            .hasCauseInstanceOf(UsageDatabaseIntegrityException.class);
        store.close();
        assertThat(Files.readString(sidecar)).isEqualTo("orphaned-sidecar-evidence");
        assertThat(paths.database()).doesNotExist();
        try (var files = Files.list(paths.database().getParent())) {
            assertThat(files).containsExactly(sidecar);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "999", "missing"})
    void unrecognizedSchemaIsRejectedBeforeAnyWrites(String version) throws Exception {
        var paths = paths();
        store = createStore();
        store.close();
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("PRAGMA journal_mode = DELETE")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("delete");
            }
            if ("missing".equals(version)) {
                statement.executeUpdate("DELETE FROM ai_statistics_meta WHERE key = 'schema_version'");
            } else {
                UsageSqliteJdbc.putMeta(connection, "schema_version", version);
            }
        }
        var original = Files.readAllBytes(paths.database());
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize).hasCauseInstanceOf(SQLException.class);
        store.close();
        assertThat(Files.readAllBytes(paths.database())).isEqualTo(original);
        try (var files = Files.list(paths.database().getParent())) {
            assertThat(files).containsExactly(paths.database());
        }
    }

    @Test
    void failedQuickCheckRejectsRecognizedDatabaseWithoutModifyingIt() throws Exception {
        var paths = paths();
        store = createStore();
        store.close();
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("PRAGMA journal_mode = DELETE")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("delete");
            }
        }
        var original = Files.readAllBytes(paths.database());
        // Claim one free page while the database has no freelist.
        java.nio.ByteBuffer.wrap(original).putInt(36, 1);
        Files.write(paths.database(), original);
        assertThat(quickCheck(paths.database())).isNotEqualTo("ok");
        store = new SqliteUsageStatisticsStore(paths);

        assertThatThrownBy(store::initialize)
            .hasCauseInstanceOf(UsageDatabaseIntegrityException.class)
            .hasRootCauseMessage("SQLite statistics quick_check failed");
        store.close();
        assertThat(Files.readAllBytes(paths.database())).isEqualTo(original);
        try (var files = Files.list(paths.database().getParent())) {
            assertThat(files).containsExactly(paths.database());
        }
    }

    @Test
    void initializesNewDatabaseWithoutRestoringOrDeletingLegacySnapshots() throws Exception {
        var paths = paths();
        store = createStore();
        var call = start("legacy-call", Instant.parse("2026-08-10T10:00:00Z"));
        store.finishCall(terminal(call, usage(2, 2)));
        store.close();
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var statement = connection.createStatement();
            var rows = statement.executeQuery("PRAGMA journal_mode = DELETE")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("delete");
        }
        var backupDirectory = paths.database().getParent().resolve("backups");
        Files.createDirectory(backupDirectory);
        var snapshot = backupDirectory.resolve("ai-foundation.sqlite.bak-20260810100000000");
        Files.move(paths.database(), snapshot);
        var original = Files.readAllBytes(snapshot);
        store = new SqliteUsageStatisticsStore(paths);

        store.initialize();

        assertThat(store.currentEpoch()).isEqualTo(1);
        assertThat(store.getCall(call.id())).isEmpty();
        assertThat(quickCheck(paths.database())).isEqualTo("ok");
        store.reset();
        assertThat(Files.readAllBytes(snapshot)).isEqualTo(original);
    }

    @Test
    void validatesExistingDatabaseIncludingUncheckpointedWal() throws Exception {
        var paths = paths();
        Files.createDirectories(paths.database().getParent());
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths.database(), new java.util.Properties());
            var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA wal_autocheckpoint = 0");
            UsageSqliteSchema.initialize(connection);
            UsageSqliteJdbc.putMeta(connection, "statistics_epoch", "7");
            assertThat(Files.size(paths.database().resolveSibling(
                paths.database().getFileName() + "-wal"))).isPositive();
            store = new SqliteUsageStatisticsStore(paths);

            store.initialize();

            assertThat(store.currentEpoch()).isEqualTo(7);
            assertThat(quickCheck(paths.database())).isEqualTo("ok");
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
    void executionRetentionFailureDoesNotChangeFactsAndCanRetry() throws Exception {
        store = createStore();
        var start = start("retention-retry", Instant.parse("2026-04-01T10:00:00Z"));
        store.startCall(start);
        store.recordExecution(new UsageExecutionRecord("expired", start.id(), start.epoch(),
            UsageUnitKind.GENERATION_STEP, 0, 0, start.startedAt(), start.startedAt(),
            UsageStatus.SUCCEEDED, null, "requested", "actual", usage(5, 5)));
        store.finishCall(terminal(start, usage(5, 5)));
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths().database(), new java.util.Properties())) {
            connection.createStatement().execute("""
                CREATE TRIGGER fail_retention BEFORE DELETE ON ai_model_executions
                BEGIN SELECT RAISE(ABORT, 'injected'); END
                """);
        }
        var clock = Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC);
        assertThatThrownBy(() -> retain(store, clock)).isInstanceOf(IllegalStateException.class);
        assertThat(store.getCall(start.id()).orElseThrow().executions()).hasSize(1);
        try (var connection = new org.sqlite.JDBC().connect(
            "jdbc:sqlite:" + paths().database(), new java.util.Properties())) {
            connection.createStatement().execute("DROP TRIGGER fail_retention");
        }
        retain(store, clock);
        retain(store, clock);
        assertThat(store.getCall(start.id()).orElseThrow().executions()).isEmpty();
        var fullDay = store.summary(query("2026-04-01T00:00:00Z", "2026-04-02T00:00:00Z"), true);
        assertThat(fullDay.callCount()).isEqualTo(1);
        assertThat(fullDay.accountedTotalTokens()).isEqualTo(10L);
        assertThat(store.summary(query("2026-04-01T12:00:00Z", "2026-04-02T00:00:00Z"), true)
            .callCount()).isZero();
    }

    @Test
    void executionRetentionKeepsBoundaryAndActiveCallsWithoutDegradingHealth() {
        store = createStore();
        var clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        var cutoff = Instant.parse("2026-09-14T00:00:00Z");
        for (var id : java.util.List.of("expired", "boundary", "active")) {
            var at = id.equals("boundary") ? cutoff : cutoff.minusMillis(1);
            var call = start(id, at);
            store.startCall(call);
            store.recordExecution(new UsageExecutionRecord(id + "-execution", id, 1,
                UsageUnitKind.GENERATION_STEP, 0, 0, at, at.plusMillis(1),
                UsageStatus.SUCCEEDED, null, "requested", "actual", usage(2, 1)));
            if (!id.equals("active")) {
                store.finishCall(terminal(call, usage(2, 1)));
            }
        }
        var health = store.readHealth();
        retain(store, clock);
        assertThat(store.getCall("expired").orElseThrow().executions()).isEmpty();
        assertThat(store.getCall("boundary").orElseThrow().executions()).hasSize(1);
        assertThat(store.getCall("active").orElseThrow().executions()).hasSize(1);
        assertThat(store.readHealth()).isEqualTo(health);
        assertThat(store.summary(query("2026-09-13T00:00:00Z", "2026-09-15T00:00:00Z"), true)
            .accountedTotalTokens()).isEqualTo(6);
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
