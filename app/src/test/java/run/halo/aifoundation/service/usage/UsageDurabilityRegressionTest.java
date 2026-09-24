package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallStart;

class UsageDurabilityRegressionTest {

    @TempDir
    Path directory;

    @Test
    void closedStoreCannotReopenUnownedConnections() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(directory));
        store.initialize();
        store.close();
        assertThatThrownBy(store::initialize).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
        store.close();
    }

    @Test
    void reusedReaderSeesNewCommitsAndIsClosedWhenIdle() throws Exception {
        var connections = new java.util.ArrayList<Connection>();
        var queries = new UsageStatisticsQueryRepository() {
            @Override
            UsageSummary summary(Connection connection, UsageQuery query, boolean complete)
                throws SQLException {
                connections.add(connection);
                return super.summary(connection, query, complete);
            }
        };
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(directory),
            new UsageStatisticsMaintenance(), queries)) {
            store.initialize();
            var query = new UsageQuery(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60),
                null, null, null, null, null, null, null, null, null);
            assertThat(store.summary(query, true).callCount()).isZero();
            store.startCall(new UsageCallStart("new-commit", 1, Instant.now(), "plugin", "1", "test",
                null, "language.generateText", "LANGUAGE", "model", "provider", "openai", "gpt", false));
            assertThat(store.summary(query, true).callCount()).isEqualTo(1);
            assertThat(connections.get(1)).isSameAs(connections.getFirst());
        }
        for (var connection : connections) {
            assertThat(connection.isClosed()).isTrue();
        }
    }

    @Test
    void failedReaderIsDiscardedBeforeAnotherQuery() {
        var connections = new java.util.ArrayList<Connection>();
        var queries = new UsageStatisticsQueryRepository() {
            @Override
            UsageSummary summary(Connection connection, UsageQuery query, boolean complete)
                throws SQLException {
                connections.add(connection);
                if (connections.size() == 1) {
                    connection.close();
                    throw new SQLException("Injected reader failure");
                }
                return super.summary(connection, query, complete);
            }
        };
        try (var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(directory),
            new UsageStatisticsMaintenance(), queries)) {
            store.initialize();
            var query = new UsageQuery(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60),
                null, null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> store.summary(query, true)).isInstanceOf(IllegalStateException.class);
            assertThat(store.summary(query, true).callCount()).isZero();
            assertThat(connections.get(1)).isNotSameAs(connections.getFirst());
        }
    }

    @Test
    void existingDatabaseAcquiresCoveringIndexWithoutChangingStatistics() throws Exception {
        var paths = new UsageDatabasePaths(directory);
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            store.startCall(new UsageCallStart("existing", 1, Instant.now(), "plugin", "1", "test",
                null, "language.generateText", "LANGUAGE", "model", "provider", "openai", "gpt", false));
        }
        try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + paths.database(),
            new java.util.Properties()); var statement = connection.createStatement()) {
            statement.execute("DROP INDEX idx_calls_aggregate_hour");
        }
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            assertThat(reopened.getCall("existing")).isPresent();
            try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + paths.database(),
                new java.util.Properties()); var statement = connection.createStatement();
                var rows = statement.executeQuery("""
                    EXPLAIN QUERY PLAN SELECT COUNT(*), SUM(input_tokens), SUM(output_tokens),
                    SUM(cache_read_input_tokens), SUM(cache_creation_input_tokens),
                    SUM(reasoning_output_tokens), SUM(accounted_total_tokens),
                    SUM(CASE WHEN status = 'SUCCEEDED' AND complete = 1
                        AND usage_quality <> 'MISSING' THEN 1 ELSE 0 END)
                    FROM ai_calls INDEXED BY idx_calls_aggregate_hour
                    WHERE started_at_ms >= 0 AND started_at_ms < 9223372036854775807
                      AND (started_at_ms / 3600000) >= 0
                    GROUP BY (started_at_ms / 3600000), status, usage_quality, complete
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("detail"))
                    .contains("COVERING INDEX idx_calls_aggregate_hour");
                while (rows.next()) {
                    assertThat(rows.getString("detail")).doesNotContain("TEMP B-TREE");
                }
            }
        }
    }

    @Test
    void closeInterruptsNativeSqliteQueryBeforeClosingItsConnection() throws Exception {
        var queries = mock(UsageStatisticsQueryRepository.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var active = new java.util.concurrent.atomic.AtomicReference<Connection>();
        org.mockito.Mockito.doAnswer(invocation -> {
            var connection = (Connection) invocation.getArgument(0);
            active.set(connection);
            entered.countDown();
            try (var statement = connection.createStatement()) {
                statement.executeQuery("""
                    WITH RECURSIVE numbers(n) AS (
                      VALUES(0) UNION ALL SELECT n + 1 FROM numbers WHERE n < 1000000000
                    ) SELECT SUM(n) FROM numbers
                    """);
            }
            return null;
        }).when(queries).summary(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(directory),
            new UsageStatisticsMaintenance(), queries);
        store.initialize();
        var query = new UsageQuery(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60),
            null, null, null, null, null, null, null, null, null);
        var reader = java.util.concurrent.CompletableFuture.runAsync(() -> store.summary(query, true));
        try {
            assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(reader.isDone()).isFalse();
            java.util.concurrent.CompletableFuture.runAsync(store::close)
                .get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThatThrownBy(() -> reader.get(2, java.util.concurrent.TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
            assertThat(active.get().isClosed()).isTrue();
        } finally {
            if (active.get() instanceof org.sqlite.SQLiteConnection sqlite && !sqlite.isClosed()) {
                sqlite.getDatabase().interrupt();
            }
            store.close();
        }
    }

    @Test
    void closePersistsBurstAndCachedMetadataWithoutDuplicatingCalls() {
        var paths = new UsageDatabasePaths(directory);
        var service = new UsageStatisticsService(new SqliteUsageStatisticsStore(paths),
            mock(CallerPluginResolver.class));
        service.initialize();
        try {
            var descriptor = new UsageCallDescriptor(new ModelCallContext(ModelType.LANGUAGE,
                "model", "provider", "openai", "gpt"), "language.streamText", true, null,
                CallerPluginInfo.builder().pluginName("plugin").detectionSource("test").build());
            for (int i = 0; i < 1501; i++) {
                var session = service.beginCall(descriptor);
                session.succeedProjection();
                session.succeed(new NormalizedUsage(10L, 5L, null, null, null, 15L, null, null),
                    "gpt", 1);
            }
        } finally {
            service.close();
        }
        assertThat(service.health().droppedEvents()).isZero();
        assertThat(service.health().writeFailures()).isZero();
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            var query = new UsageQuery(Instant.now().minusSeconds(3600), Instant.now().plusSeconds(60),
                null, null, null, null, null, null, null, null, null);
            var summary = reopened.summary(query, true);
            assertThat(summary.callCount()).isEqualTo(1501);
            assertThat(summary.accountedTotalTokens()).isEqualTo(1501L * 15);
            assertThat(summary.missingUsageCalls()).isZero();
        }
    }

    @Test
    void rollbackFailureClosesConnectionWithoutCommittingPartialBatch() throws Exception {
        var paths = new UsageDatabasePaths(directory);
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            var field = SqliteUsageStatisticsStore.class.getDeclaredField("writer");
            field.setAccessible(true);
            var connection = (Connection) field.get(store);
            var autoCommits = new AtomicInteger();
            var proxy = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (ignored, method, arguments) -> {
                    if (method.getName().equals("rollback")) {
                        throw new SQLException("Injected rollback failure");
                    }
                    if (method.getName().equals("setAutoCommit") && Boolean.TRUE.equals(arguments[0])) {
                        autoCommits.incrementAndGet();
                    }
                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
            field.set(store, proxy);
            var start = new UsageCallStart("rollback", 1, Instant.now(), "plugin", "1", "test",
                null, "language.generateText", "LANGUAGE", "model", "provider", "openai", "gpt", false);
            assertThatThrownBy(() -> store.writeBatch(List.of(() -> store.startCall(start),
                    () -> { throw new IllegalStateException("Injected write failure"); })))
                .isInstanceOf(IllegalStateException.class);
            assertThat(autoCommits.get()).isZero();
            assertThat(connection.isClosed()).isTrue();
            assertThat(store.getCall(start.id())).isEmpty();
        }
    }
}
