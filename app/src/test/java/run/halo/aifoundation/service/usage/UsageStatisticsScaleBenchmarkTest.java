package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;

class UsageStatisticsScaleBenchmarkTest {

    private static final int CALL_COUNT = 1_000_000;
    private static final int EXECUTIONS_PER_CALL = 5;
    private static final int BATCH_SIZE = 5_000;
    private static final Instant RANGE_END = Instant.parse("2026-08-11T00:00:00Z");
    private static final long RANGE_MILLIS = 90L * 24 * 60 * 60 * 1_000;

    @TempDir
    Path temporaryDirectory;

    @Test
    void benchmarkMillionCallsAndFiveMillionExecutions() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("AI_USAGE_BENCHMARK")),
            "Set AI_USAGE_BENCHMARK=true to run the scale benchmark");
        var paths = new UsageDatabasePaths(temporaryDirectory.resolve("plugins"));
        var store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.close();

        var loadStarted = System.nanoTime();
        try (var connection = connection(paths.database())) {
            loadDataset(connection);
        }
        var loadMillis = elapsedMillis(loadStarted);

        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var activeStore = store;
        var enqueueP95 = enqueueP95();
        var rollupStarted = System.nanoTime();
        store.rollupAndRetain(
            Clock.fixed(Instant.parse("2026-08-12T02:00:00Z"), ZoneOffset.UTC));
        var rollupMillis = elapsedMillis(rollupStarted);

        var query = query(null);
        var filtered = query("plugin-7");
        var listP95 = p95(25, () -> activeStore.listCalls(filtered, 50, null));
        var deepCursorP95 = deepCursorP95(activeStore, filtered);
        var summaryP95 = p95(40, () -> activeStore.summary(query, true));
        var trendsP95 = p95(40, () -> activeStore.trends(query, true));
        var first = activeStore.listCalls(query, 1, null).items().getFirst();
        var detailP95 = p95(25, () -> activeStore.getCall(first.id()));

        var backupStarted = System.nanoTime();
        store.backup();
        var backupMillis = elapsedMillis(backupStarted);
        Files.delete(paths.database());
        var restoreStarted = System.nanoTime();
        var restoredStore = new SqliteUsageStatisticsStore(paths);
        restoredStore.initialize();
        var restoreMillis = elapsedMillis(restoreStarted);
        assertThat(restoredStore.readHealth().integrityError())
            .isEqualTo("RECOVERED_FROM_SNAPSHOT");
        restoredStore.close();
        var quickCheckStarted = System.nanoTime();
        assertThat(quickCheck(paths.database())).isEqualTo("ok");
        var quickCheckMillis = elapsedMillis(quickCheckStarted);

        var databaseBytes = Files.size(paths.database());
        var wal = paths.database().resolveSibling(paths.database().getFileName() + "-wal");
        var walBytes = Files.exists(wal) ? Files.size(wal) : 0;
        var plans = queryPlans(paths.database());
        System.out.printf("""
            AI usage scale benchmark
            calls=%d executions=%d load=%.2fms rollup=%.2fms
            enqueueP95=%.5fms
            listP95=%.2fms deepCursorP95=%.2fms detailP95=%.2fms
            summaryP95=%.2fms trendsP95=%.2fms
            backup=%.2fms restore=%.2fms quickCheck=%.2fms databaseBytes=%d walBytes=%d
            plans=%s%n
            """, CALL_COUNT, CALL_COUNT * EXECUTIONS_PER_CALL, loadMillis, rollupMillis,
            enqueueP95, listP95, deepCursorP95, detailP95, summaryP95, trendsP95, backupMillis,
            restoreMillis, quickCheckMillis, databaseBytes, walBytes, plans);

        assertThat(listP95).isLessThanOrEqualTo(200D);
        assertThat(enqueueP95).isLessThanOrEqualTo(1D);
        assertThat(deepCursorP95).isLessThanOrEqualTo(200D);
        assertThat(summaryP95).isLessThanOrEqualTo(500D);
        assertThat(trendsP95).isLessThanOrEqualTo(500D);
    }

    private static double enqueueP95() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var blocked = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            blocked.await();
            return null;
        }).when(store).startCall(org.mockito.ArgumentMatchers.any());
        var resolver = mock(CallerPluginResolver.class);
        when(resolver.resolveCurrentCallerSnapshot()).thenReturn(CallerPluginInfo.builder()
            .pluginName("benchmark").version("1").detectionSource("benchmark").build());
        var service = new UsageStatisticsService(store, resolver);
        service.initialize();
        var samples = new ArrayList<Double>(UsageStatisticsService.WRITE_QUEUE_CAPACITY + 100);
        var descriptor = new UsageCallDescriptor(new ModelCallContext(ModelType.LANGUAGE,
            "model", "provider", "openai", "gpt"),
            UsageOperation.LANGUAGE_GENERATE_TEXT.value(), false, null,
            resolver.resolveCurrentCallerSnapshot());
        for (int index = 0; index < UsageStatisticsService.WRITE_QUEUE_CAPACITY + 100; index++) {
            var started = System.nanoTime();
            service.beginCall(descriptor);
            samples.add(elapsedMillis(started));
        }
        blocked.countDown();
        service.close();
        Collections.sort(samples);
        return samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
    }

    private static void loadDataset(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try (var calls = connection.prepareStatement("""
            INSERT INTO ai_calls (
              id, epoch, started_at_ms, completed_at_ms, duration_ms, caller_plugin_name,
              caller_detection_source, feature, operation, model_type, model_name,
              provider_name, provider_type, request_model_id, response_model_id, streaming,
              status, step_count, attempt_count, missing_execution_count, complete,
              input_tokens, output_tokens, accounted_total_tokens, usage_quality
            ) VALUES (?, 1, ?, ?, 50, ?, 'benchmark', ?, ?, ?, ?, ?, ?, ?, ?, 0, ?,
              1, 5, 0, 1, ?, ?, ?, ?)
            """);
            var executions = connection.prepareStatement("""
                INSERT INTO ai_model_executions (
                  id, call_id, epoch, unit_kind, unit_index, attempt_index, started_at_ms,
                  completed_at_ms, status, request_model_id, response_model_id,
                  input_tokens, output_tokens, accounted_total_tokens, usage_quality
                ) VALUES (?, ?, 1, 'GENERATION_STEP', 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int callIndex = 0; callIndex < CALL_COUNT; callIndex++) {
                var callId = "call-" + callIndex;
                var startedAt = RANGE_END.toEpochMilli() - RANGE_MILLIS
                    + Math.floorMod(callIndex * 7_919L, RANGE_MILLIS);
                var missing = callIndex % 10 == 0;
                var failed = callIndex % 20 == 0;
                bindCall(calls, callIndex, callId, startedAt, missing, failed);
                calls.addBatch();
                for (int attempt = 0; attempt < EXECUTIONS_PER_CALL; attempt++) {
                    bindExecution(executions, callIndex, callId, attempt, startedAt, missing,
                        failed && attempt == EXECUTIONS_PER_CALL - 1);
                    executions.addBatch();
                }
                if ((callIndex + 1) % BATCH_SIZE == 0) {
                    calls.executeBatch();
                    executions.executeBatch();
                    connection.commit();
                }
            }
            calls.executeBatch();
            executions.executeBatch();
            connection.commit();
        }
    }

    private static void bindCall(PreparedStatement statement, int index, String id,
        long startedAt, boolean missing, boolean failed) throws Exception {
        var parameter = 1;
        statement.setString(parameter++, id);
        statement.setLong(parameter++, startedAt);
        statement.setLong(parameter++, startedAt + 50);
        statement.setString(parameter++, "plugin-" + index % 20);
        statement.setString(parameter++, index % 2 == 0 ? "semantic-search" : "editor");
        statement.setString(parameter++, index % 2 == 0
            ? UsageOperation.LANGUAGE_GENERATE_TEXT.value()
            : UsageOperation.EMBEDDING_EMBED.value());
        statement.setString(parameter++, index % 2 == 0 ? "LANGUAGE" : "EMBEDDING");
        statement.setString(parameter++, "model-" + index % 50);
        statement.setString(parameter++, "provider-" + index % 10);
        statement.setString(parameter++, index % 2 == 0 ? "openai" : "ollama");
        statement.setString(parameter++, "requested-model");
        statement.setString(parameter++, "actual-model");
        statement.setString(parameter++, failed ? "FAILED" : "SUCCEEDED");
        setUsage(statement, parameter, missing, 100L, 50L);
    }

    private static void bindExecution(PreparedStatement statement, int callIndex, String callId,
        int attempt, long startedAt, boolean missing, boolean failed) throws Exception {
        var parameter = 1;
        statement.setString(parameter++, "execution-" + callIndex + '-' + attempt);
        statement.setString(parameter++, callId);
        statement.setInt(parameter++, attempt);
        statement.setLong(parameter++, startedAt + attempt);
        statement.setLong(parameter++, startedAt + attempt + 1);
        statement.setString(parameter++, failed ? "FAILED" : "SUCCEEDED");
        statement.setString(parameter++, "requested-model");
        statement.setString(parameter++, "actual-model");
        setUsage(statement, parameter, missing, 20L, 10L);
    }

    private static void setUsage(PreparedStatement statement, int parameter, boolean missing,
        long input, long output) throws Exception {
        if (missing) {
            statement.setObject(parameter++, null);
            statement.setObject(parameter++, null);
            statement.setObject(parameter++, null);
            statement.setString(parameter, "MISSING");
            return;
        }
        statement.setLong(parameter++, input);
        statement.setLong(parameter++, output);
        statement.setLong(parameter++, input + output);
        statement.setString(parameter, "REPORTED_COMPONENTS");
    }

    private static UsageQuery query(String caller) {
        return new UsageQuery(RANGE_END.minusMillis(RANGE_MILLIS), RANGE_END, caller, null,
            null, null, null, null, null, null, null);
    }

    private static double p95(int runs, Runnable operation) {
        var samples = new ArrayList<Double>(runs);
        for (int i = 0; i < runs; i++) {
            var started = System.nanoTime();
            operation.run();
            samples.add(elapsedMillis(started));
        }
        Collections.sort(samples);
        return samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
    }

    private static double deepCursorP95(SqliteUsageStatisticsStore store, UsageQuery query) {
        var samples = new ArrayList<Double>();
        String cursor = null;
        for (int i = 0; i < 100; i++) {
            var started = System.nanoTime();
            var page = store.listCalls(query, 50, cursor);
            samples.add(elapsedMillis(started));
            cursor = page.nextCursor();
            if (cursor == null) {
                break;
            }
        }
        Collections.sort(samples);
        return samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
    }

    private static String quickCheck(Path database) throws Exception {
        try (var connection = connection(database);
            var rows = connection.createStatement().executeQuery("PRAGMA quick_check")) {
            return rows.next() ? rows.getString(1) : "no-result";
        }
    }

    private static List<String> queryPlans(Path database) throws Exception {
        var plans = new ArrayList<String>();
        try (var connection = connection(database)) {
            addPlan(connection, plans, "calls", """
                SELECT * FROM ai_calls
                WHERE caller_plugin_name = 'plugin-7' AND started_at_ms >= 0
                ORDER BY started_at_ms DESC, id DESC LIMIT 50
                """);
            addPlan(connection, plans, "executions", """
                SELECT * FROM ai_model_executions
                WHERE call_id = 'call-999999' ORDER BY started_at_ms, id
                """);
            addPlan(connection, plans, "daily-calls", """
                SELECT * FROM ai_usage_daily WHERE day >= '2026-05-13' AND day < '2026-08-11'
                """);
            addPlan(connection, plans, "daily-tokens", """
                SELECT * FROM ai_token_usage_daily
                WHERE day >= '2026-05-13' AND day < '2026-08-11'
                """);
        }
        return List.copyOf(plans);
    }

    private static void addPlan(Connection connection, List<String> plans, String name, String sql)
        throws Exception {
        try (var rows = connection.createStatement().executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rows.next()) {
                plans.add(name + ": " + rows.getString("detail"));
            }
        }
    }

    private static Connection connection(Path database) throws Exception {
        return new org.sqlite.JDBC().connect("jdbc:sqlite:" + database, new Properties());
    }

    private static double elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000D;
    }
}
