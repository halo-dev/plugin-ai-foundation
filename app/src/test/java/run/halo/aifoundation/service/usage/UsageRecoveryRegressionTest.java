package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageUnitKind;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.provider.support.ModelType;

class UsageRecoveryRegressionTest {
    @TempDir
    Path temp;

    private UsageCallStart start(String id, String time) {
        return new UsageCallStart(id, 1, Instant.parse(time), "plugin", "1", "test", null,
            "language.generateText", "LANGUAGE", "model", "provider", "openai", "gpt", false);
    }
    private UsageQuery range() {
        return new UsageQuery(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"),
            null, null, null, null, null, null, null, null, null);
    }

    @Test
    void linkageErrorRollsBackEntireSqliteBatch() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            var call = start("partial-batch", "2026-09-10T00:00:00Z");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.writeBatch(java.util.List.of(
                () -> store.startCall(call),
                () -> { throw new NoClassDefFoundError("injected-after-first-write"); }
            ))).isInstanceOf(NoClassDefFoundError.class);
            assertThat(store.getCall(call.id())).isEmpty();
        } finally {
            store.close();
        }
    }

    @Test
    void recoversDurableExecutionTokensAndArchivesThem() {
        var paths = new UsageDatabasePaths(temp);
        var store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var call = start("interrupted", "2026-09-10T00:00:00Z");
        store.startCall(call);
        store.recordExecution(new UsageExecutionRecord("execution", call.id(), 1,
            UsageUnitKind.GENERATION_STEP, 0, 0, call.startedAt(), call.startedAt().plusSeconds(1),
            UsageStatus.SUCCEEDED, null, "gpt", "gpt",
            new NormalizedUsage(10L, 5L,null, null, null, null, null, null)));
        store.close(); // All existing writes are durable; only terminal event is absent.
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.reconcileAbandoned(Instant.parse("2026-09-11T00:00:00Z"));
        try {
            var detail = store.getCall(call.id()).orElseThrow();
            assertThat(detail.executions().getFirst().usage().accountedTotalTokens()).isEqualTo(15);
            assertThat(detail.call().usage().accountedTotalTokens()).isEqualTo(15);
            assertThat(detail.call().usage().quality()).isEqualTo(UsageQuality.PARTIAL);
            assertThat(detail.call().complete()).isFalse();
            assertThat(detail.call().attemptCount()).isEqualTo(1);
            assertThat(store.summary(range(), true).accountedTotalTokens()).isEqualTo(15);
            store.rollupAndRetain(Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
            assertThat(store.getCall(call.id())).isEmpty();
            assertThat(store.summary(range(), true).accountedTotalTokens()).isEqualTo(15);
            assertThat(store.summary(range(), true).complete()).isFalse();
        } finally {
            store.close();
        }
    }

    @Test
    void archivesCompletedCallsWithoutLosingActiveOrLateCalls() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            store.startCall(start("terminal-event-lost", "2025-01-01T00:00:00Z"));
            var completed = start("completed", "2025-01-01T01:00:00Z");
            store.finishCall(new UsageCallTerminal(completed, completed.startedAt().plusSeconds(1),
                UsageStatus.SUCCEEDED, null, "gpt", 1, 0, 0, true,
                new NormalizedUsage(10L, 5L, null, null, null, null, null, null)));
            store.rollupAndRetain(Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC));
            assertThat(store.getCall(completed.id())).isEmpty();
            assertThat(store.getCall("terminal-event-lost")).isPresent();
            assertThat(store.summary(range(), true).callCount()).isEqualTo(2);
            assertThat(store.trends(range(), true).stream().mapToLong(UsageTrendPoint::callCount).sum()).isEqualTo(2);
            var late = start("terminal-event-lost", "2025-01-01T00:00:00Z");
            store.finishCall(new UsageCallTerminal(late, Instant.parse("2026-09-11T00:00:00Z"),
                UsageStatus.SUCCEEDED, null, "gpt", 1, 0, 0, true,
                new NormalizedUsage(10L, 5L,null, null, null, null, null, null)));
            var maintenanceClock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC);
            store.rollupAndRetain(maintenanceClock);
            store.rollupAndRetain(maintenanceClock);
            assertThat(store.summary(range(), true).callCount()).isEqualTo(2);
            assertThat(store.summary(range(), true).accountedTotalTokens()).isEqualTo(30);
            assertThat(store.getCall(late.id())).isEmpty();
        } finally {
            store.close();
        }
    }

    @Test
    void writerLinkageFailureDisablesStatisticsAndReportsLostEvents() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var attempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            attempted.countDown();
            throw new NoClassDefFoundError("injected-driver-linkage");
        })
            .when(store).writeBatch(anyList());
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        try {
            var caller = CallerPluginInfo.builder().pluginName("plugin").version("1")
                .detectionSource("test").build();
            var session = service.beginCall(new UsageCallDescriptor(new ModelCallContext(
                ModelType.LANGUAGE, "model", "provider", "openai", "gpt"), "language.generateText",false, null, caller));
            assertThat(attempted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(service.health().queueDepth()).isZero();
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (service.health().droppedEvents() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(service.health().complete()).isFalse();
            assertThat(service.health().available()).isFalse();
            assertThat(service.health().droppedEvents()).isEqualTo(1);
            assertThat(service.health().writeFailures()).isEqualTo(1);
            assertThat(session.isIncomplete()).isTrue();
            verify(store, times(1)).writeBatch(anyList());
        } finally {
            service.close();
        }
    }
}
