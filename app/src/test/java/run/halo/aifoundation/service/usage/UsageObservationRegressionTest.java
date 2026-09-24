package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.audit.AuditedLanguageModel;
import run.halo.aifoundation.service.audit.CallerPluginAuditRecorder;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageUnitKind;

class UsageObservationRegressionTest {
    @TempDir
    Path temp;
    private UsageCallStart start() {
        return new UsageCallStart("review", 1, Instant.parse("2026-06-01T10:00:00Z"),
            "plugin", "1", "stack", null, "language.generateText", "LANGUAGE",
            "model", "provider", "openai", "requested", false);
    }
    private UsageQuery query(String from, String to) {
        return new UsageQuery(Instant.parse(from), Instant.parse(to), null,null,null,null,null,null,null,null,null);
    }
    @Test
    void historicalPartialDayMustNotSilentlyDisappear() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            var start = start();
            store.startCall(start);
            store.finishCall(new UsageCallTerminal(start,start.startedAt().plusSeconds(1),
                UsageStatus.SUCCEEDED,null,"actual",1,0,0,true,
                new NormalizedUsage(10L,5L,null,null,null,null,null,null)));
            store.rollupAndRetain(Clock.fixed(Instant.parse("2026-09-10T02:00:00Z"),ZoneOffset.UTC));
            var summary = store.summary(query("2026-06-01T08:00:00Z","2026-06-01T20:00:00Z"),true);
            assertThat(summary.preciseRange()).as("expired sub-day facts must be disclosed").isFalse();
            assertThat(summary.callCount()).isEqualTo(1);
            assertThat(summary.dataFrom()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        } finally {
            store.close();
        }
    }
    @Test
    void lateCompletionMustBeVisibleAfterRollup() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            var start = start();
            store.startCall(start);
            store.rollupAndRetain(Clock.fixed(Instant.parse("2026-06-02T02:00:00Z"),ZoneOffset.UTC));
            store.finishCall(new UsageCallTerminal(start,Instant.parse("2026-06-02T03:00:00Z"),
                UsageStatus.SUCCEEDED,null,"actual",1,0,0,true,
                new NormalizedUsage(10L,5L,null,null,null,null,null,null)));
            var summary = store.summary(query("2026-06-01T00:00:00Z","2026-06-02T00:00:00Z"),true);
            assertThat(summary.successCount()).isEqualTo(1);
        } finally {
            store.close();
        }
    }
    @Test
    void cachedTextProjectionMustFinishLogicalCall() {
        var statistics = mock(UsageStatisticsService.class);
        var delegate = mock(LanguageModel.class);
        var session = mock(UsageCallSession.class);
        var context = new ModelCallContext(ModelType.LANGUAGE,"model","provider","openai","gpt");
        var descriptor = mock(UsageCallDescriptor.class);
        var request = GenerateTextRequest.builder().prompt("test").build();
        when(statistics.describeCall(any(),anyString(),anyBoolean(),any())).thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        when(delegate.streamText(request)).thenReturn(new StreamTextResult(Flux.empty(),Flux.just("cached"),
            Flux.empty(),Flux.empty(),Mono.empty(),Mono.just(GenerateTextResult.builder().text("cached").build())));
        var model = new AuditedLanguageModel(delegate,context,mock(CallerPluginAuditRecorder.class),statistics);
        assertThat(model.streamText(request).textStream().collectList().block()).containsExactly("cached");
        verify(session).succeedProjection();
    }
    @Test
    void statisticsStartFailureMustNotFailModelCall() {
        var statistics = mock(UsageStatisticsService.class);
        var delegate = mock(LanguageModel.class);
        var context = new ModelCallContext(ModelType.LANGUAGE,"model","provider","openai","gpt");
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(any(),anyString(),anyBoolean(),any())).thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenThrow(new IllegalStateException("telemetry failure"));
        when(delegate.generateText("test")).thenReturn(Mono.just(GenerateTextResult.builder().text("ok").build()));
        var model = new AuditedLanguageModel(delegate,context,mock(CallerPluginAuditRecorder.class),statistics);
        assertThat(model.generateText("test").block().getText()).isEqualTo("ok");
    }
    @Test
    void extractionFailureMustStillRecordExecution() {
        var service = mock(UsageStatisticsService.class);
        var session = new UsageCallSession(service,start(),Clock.systemUTC());
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.GENERATION_STEP,0,
            () -> Mono.just("ok"), ignored -> {throw new IllegalStateException("bad usage");}, ignored -> "model")
            .contextWrite(ctx -> ctx.put(UsageCallSession.REACTOR_CONTEXT_KEY,session));
        assertThat(observed.block()).isEqualTo("ok");
        verify(service).recordExecution(eq(session),any());
    }

    @Test
    void cancellingAuditedCallIncludesUpstreamExecutionEvidence() {
        var statistics = mock(UsageStatisticsService.class);
        var session = new UsageCallSession(statistics, start(), Clock.systemUTC());
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(any(), anyString(), anyBoolean(), any())).thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        var delegate = mock(LanguageModel.class);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.GENERATION_STEP, 0,
            Mono::<GenerateTextResult>never, ignored -> NormalizedUsage.missing(), ignored -> null);
        when(delegate.generateText("test")).thenReturn(observed);
        var context = new ModelCallContext(ModelType.LANGUAGE, "model", "provider", "openai", "gpt");
        var model = new AuditedLanguageModel(delegate, context,
            mock(CallerPluginAuditRecorder.class), statistics);
        var subscription = model.generateText("test").subscribe();
        subscription.dispose();
        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(statistics).finishCall(eq(session), terminal.capture());
        assertThat(terminal.getValue().status()).isEqualTo(UsageStatus.CANCELLED);
        assertThat(terminal.getValue().attemptCount()).isEqualTo(1);
        assertThat(terminal.getValue().missingExecutionCount()).isEqualTo(1);
    }

    @Test
    void terminalWaitsForConcurrentAttemptFactsWithoutWaitingOnTheCaller() {
        var sink = mock(UsageStatisticsService.class);
        var session = new UsageCallSession(sink, start(), Clock.systemUTC());
        var first = session.beginExecution(UsageUnitKind.EMBEDDING_BATCH, 0);
        var second = session.beginExecution(UsageUnitKind.EMBEDDING_BATCH, 1);
        first.succeed(new NormalizedUsage(10L, 0L, null, null, null, null, null, null), null);
        session.cancel();
        verify(sink, never()).finishCall(any(), any());
        second.cancel(NormalizedUsage.missing(), null);
        var value = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(sink).finishCall(eq(session), value.capture());
        assertThat(value.getValue().status()).isEqualTo(UsageStatus.CANCELLED);
        assertThat(value.getValue().usage().quality()).isEqualTo(UsageQuality.PARTIAL);
        assertThat(value.getValue().usage().accountedTotalTokens()).isEqualTo(10L);
        assertThat(value.getValue().missingExecutionCount()).isEqualTo(1);
    }

    @Test
    void resetRetiresRecoverySnapshotsAndKeepsSchemaValid() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        var store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.startCall(start());
        store.backup();
        assertThat(UsageSqliteFiles.listBackups(paths)).isNotEmpty();
        store.reset();
        assertThat(UsageSqliteFiles.listBackups(paths)).isEmpty();
        store.close();
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        try {
            assertThat(store.currentEpoch()).isEqualTo(2);
            assertThat(store.getCall(start().id())).isEmpty();
        } finally {
            store.close();
        }
    }

    @Test
    void batchFailureRollsBackAllWrites() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.writeBatch(java.util.List.of(
                () -> store.startCall(start()),
                () -> { throw new IllegalStateException("injected"); }
            ))).isInstanceOf(IllegalStateException.class);
            assertThat(store.getCall(start().id())).isEmpty();
        } finally {
            store.close();
        }
    }

    @Test
    void realSqliteWriterKeepsEveryCallWhileQueriesRunConcurrently() throws Exception {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        var resolver = mock(CallerPluginResolver.class);
        var caller = CallerPluginInfo.builder().pluginName("load-test").version("1")
            .detectionSource("test").build();
        when(resolver.resolveCurrentCallerSnapshot()).thenReturn(caller);
        var service = new UsageStatisticsService(store, resolver);
        service.initialize();
        var descriptor = new UsageCallDescriptor(new ModelCallContext(ModelType.LANGUAGE,
            "model", "provider", "openai", "gpt"), "language.generateText", false, null, caller);
        var range = new UsageQuery(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60),
            null, null, null, null, null, null, null, null, null);
        var samples = new long[1000];
        try (var reader = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var reading = reader.submit(() -> {
                for (int i = 0; i < 50; i++) {
                    service.summary(range).block();
                    service.trends(range).block();
                }
            });
            for (int i = 0; i < samples.length; i++) {
                var started = System.nanoTime();
                var call = service.beginCall(descriptor);
                call.beginExecution(UsageUnitKind.GENERATION_STEP, 0).succeed(
                    new NormalizedUsage(10L, 5L, null, null, null, null, null, null), "gpt");
                call.succeed(NormalizedUsage.missing(), "gpt", 1);
                samples[i] = System.nanoTime() - started;
            }
            reading.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (service.summary(range).block().successCount() < samples.length
                && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            var summary = service.summary(range).block();
            assertThat(summary.callCount()).isEqualTo(1000);
            assertThat(summary.successCount()).isEqualTo(1000);
            assertThat(summary.accountedTotalTokens()).isEqualTo(15000L);
            assertThat(service.health().droppedEvents()).isZero();
            assertThat(service.health().writeFailures()).isZero();
            java.util.Arrays.sort(samples);
            System.out.println("Real SQLite mixed workload: 1000 calls, 3000 events, lifecycle p95="
                + samples[949] / 1_000_000D + " ms");
            assertThat(samples[949]).isLessThan(1_000_000L);
        } finally {
            service.close();
        }
    }

    @Test
    void partialUsageIsVisibleInSummaryAndTrendsEvenWithCompleteDelivery() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            var partial = NormalizedUsage.sum(java.util.List.of(NormalizedUsage.missing(),
                new NormalizedUsage(10L, 5L, null, null, null, null, null, null)));
            store.finishCall(new UsageCallTerminal(start(), start().startedAt().plusSeconds(1),
                UsageStatus.SUCCEEDED, null, null, 1, 2, 1, true, partial));
            var range = query("2026-06-01T00:00:00Z", "2026-06-02T00:00:00Z");
            var summary = store.summary(range, true);
            assertThat(summary.complete()).isTrue();
            assertThat(summary.partialUsageCalls()).isEqualTo(1);
            assertThat(summary.completeUsageCoverage()).isZero();
            assertThat(summary.accountedTotalTokens()).isEqualTo(15L);
            assertThat(store.trends(range, true).getFirst().partialUsageCalls()).isEqualTo(1);
        } finally {
            store.close();
        }
    }
}
