package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.audit.AuditedLanguageModel;
import run.halo.aifoundation.service.audit.CallerPluginAuditRecorder;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;

class UsageWriterFailureRegressionTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ModelCallContext MODEL_CONTEXT = new ModelCallContext(
        ModelType.LANGUAGE, "model", "provider", "openai", "gpt-test");

    @TempDir
    Path directory;

    @Test
    void sqliteFullDisablesStatisticsAndPreservesModelResults() throws Exception {
        try (var fixture = new Fixture()) {
            int pages;
            try (var statement = fixture.connection.createStatement();
                var rows = statement.executeQuery("PRAGMA page_count")) {
                assertThat(rows.next()).isTrue();
                pages = rows.getInt(1);
            }
            // Exhaust a real SQLite database without filling the host filesystem.
            try (var statement = fixture.connection.createStatement()) {
                statement.execute("PRAGMA max_page_count = " + pages);
            }
            for (int calls = 0; calls < 300 && !fixture.connection.isClosed(); calls++) {
                var session = fixture.service.beginCall(fixture.descriptor);
                assertThat(session).isNotNull();
                session.succeed("gpt-test", 1);
                fixture.awaitWriter();
            }

            assertThat(fixture.connection.isClosed()).as("SQLite full invalidated writer")
                .isTrue();
            fixture.assertDisabledWithoutAffectingModels();
        }
    }

    @Test
    void rollbackFailureDisablesStatisticsAndPreservesModelResults() throws Exception {
        try (var fixture = new Fixture()) {
            var firstCommit = new AtomicBoolean(true);
            var proxy = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Connection.class}, (ignored, method, arguments) -> {
                    if (method.getName().equals("commit") && firstCommit.getAndSet(false)) {
                        throw new SQLException("Injected commit I/O failure", null, 10);
                    }
                    if (method.getName().equals("rollback")) {
                        throw new SQLException("Injected rollback failure");
                    }
                    try {
                        return method.invoke(fixture.connection, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
            var field = SqliteUsageStatisticsStore.class.getDeclaredField("writer");
            field.setAccessible(true);
            field.set(fixture.store, proxy);

            assertThat(fixture.service.beginCall(fixture.descriptor)).isNotNull();
            fixture.awaitWriter();

            assertThat(firstCommit).isFalse();
            assertThat(fixture.connection.isClosed()).isTrue();
            fixture.assertDisabledWithoutAffectingModels();
        }
    }

    private final class Fixture implements AutoCloseable {
        private final SqliteUsageStatisticsStore store;
        private final UsageStatisticsService service;
        private final UsageCallDescriptor descriptor;
        private final Connection connection;
        private final ThreadPoolExecutor writer;

        private Fixture() throws Exception {
            store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(directory));
            var resolver = mock(CallerPluginResolver.class);
            when(resolver.resolveCurrentCallerSnapshot()).thenReturn(CallerPluginInfo.builder()
                .pluginName("consumer").version("1").detectionSource("test").build());
            service = new UsageStatisticsService(store, resolver);
            service.initialize();
            assertThat(service.health().available()).isTrue();
            descriptor = service.describeCall(MODEL_CONTEXT, "language.generateText", false, null);
            var connectionField = SqliteUsageStatisticsStore.class.getDeclaredField("writer");
            connectionField.setAccessible(true);
            connection = (Connection) connectionField.get(store);
            var writerField = UsageStatisticsService.class.getDeclaredField("writer");
            writerField.setAccessible(true);
            writer = (ThreadPoolExecutor) writerField.get(service);
        }

        private void awaitWriter() throws Exception {
            writer.submit(() -> { }).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void assertDisabledWithoutAffectingModels() throws Exception {
            var health = service.health();
            assertThat(health.available()).as("closed writer disables statistics").isFalse();
            assertThat(health.complete()).isFalse();
            assertThat(health.writeFailures()).isPositive();
            assertThat(health.queueDepth()).isZero();
            assertThat(service.beginCall(descriptor)).as("disabled statistics reject admission")
                .isNull();

            var now = Instant.now();
            var query = new UsageQuery(now.minusSeconds(60), now.plusSeconds(60),
                null, null, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.summary(query).block(TIMEOUT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unavailable");
            assertThatThrownBy(() -> service.reset("RESET").block(TIMEOUT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unavailable");

            var delegate = mock(LanguageModel.class);
            var result = GenerateTextResult.builder().text("answer").build();
            var failure = new IllegalArgumentException("provider failure");
            when(delegate.generateText("success")).thenReturn(Mono.just(result));
            when(delegate.generateText("failure")).thenReturn(Mono.error(failure));
            var model = new AuditedLanguageModel(delegate, MODEL_CONTEXT,
                mock(CallerPluginAuditRecorder.class), service);
            assertThat(model.generateText("success").block(TIMEOUT)).isSameAs(result);
            assertThatThrownBy(() -> model.generateText("failure").block(TIMEOUT))
                .isSameAs(failure);
            awaitWriter();

            // No new statistics work, retries, or drop accounting after admission is disabled.
            assertThat(service.health()).isEqualTo(health);
            assertThat(connection.isClosed()).isTrue();
        }

        @Override
        public void close() {
            service.close();
        }
    }
}
