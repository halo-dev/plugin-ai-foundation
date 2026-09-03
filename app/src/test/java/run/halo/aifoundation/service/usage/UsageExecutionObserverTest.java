package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.chat.LanguageModelUsage;

class UsageExecutionObserverTest {

    @Test
    void recordsFirstAttemptSuccess() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.RERANK, 0,
                () -> Mono.just("ok"),
                ignored -> new NormalizedUsage(4L, 2L, null, null, null, null, null, null),
                ignored -> "actual")
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectNext("ok").verifyComplete();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().attemptIndex()).isZero();
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.SUCCEEDED);
        assertThat(execution.getValue().usage().accountedTotalTokens()).isEqualTo(6L);
    }

    @Test
    void emptyMonoCompletesWithoutTelemetryFailure() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.RERANK, 0,
                Mono::<String>empty,
                ignored -> {
                    throw new AssertionError("extractor must not be called for an empty Mono");
                },
                ignored -> {
                    throw new AssertionError("extractor must not be called for an empty Mono");
                })
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).verifyComplete();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.SUCCEEDED);
        assertThat(execution.getValue().usage().quality()).isEqualTo(UsageQuality.MISSING);
    }

    @Test
    void missingProviderUsageDoesNotMakePersistedTelemetryIncomplete() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);

        session.beginExecution(UsageUnitKind.RERANK, 0)
            .succeed(NormalizedUsage.missing(), null);
        session.succeed(NormalizedUsage.missing(), null, 1);

        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(service).finishCall(org.mockito.ArgumentMatchers.any(), terminal.capture());
        assertThat(terminal.getValue().complete()).isTrue();
        assertThat(terminal.getValue().missingExecutionCount()).isEqualTo(1);
        assertThat(terminal.getValue().usage().quality()).isEqualTo(UsageQuality.MISSING);
    }

    @Test
    void monoExtractorFailureDoesNotFailModelResult() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.RERANK, 0,
                () -> Mono.just("ok"),
                ignored -> {
                    throw new IllegalStateException("broken usage metadata");
                }, ignored -> "actual")
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectNext("ok").verifyComplete();
    }

    @Test
    void fluxExtractorFailureDoesNotFailModelStream() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observeFlux(UsageUnitKind.GENERATION_STEP, 0,
                () -> Flux.just("first", "second"),
                value -> {
                    if ("second".equals(value)) {
                        throw new IllegalStateException("broken usage metadata");
                    }
                    return new NormalizedUsage(3L, 2L, null, null, null, null, null, null);
                }, ignored -> "actual")
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectNext("first", "second").verifyComplete();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().usage().accountedTotalTokens()).isEqualTo(5L);
    }

    @Test
    void createsOnePhysicalAttemptPerActualRetryAndUsesChildUsageForParent() {
        var service = mock(UsageStatisticsService.class);
        var start = new UsageCallStart("call", 1, Instant.parse("2026-08-11T00:00:00Z"),
            "plugin", "1", "stack", null, "language.generateText", "LANGUAGE", "model",
            "provider", "openai", "requested", false);
        var session = new UsageCallSession(service, start,
            Clock.fixed(Instant.parse("2026-08-11T00:00:01Z"), ZoneOffset.UTC));
        var attempts = new AtomicInteger();
        var observer = new UsageExecutionObserver();
        var observed = observer.observe(UsageUnitKind.GENERATION_STEP, 0,
                () -> attempts.getAndIncrement() == 0
                    ? Mono.error(new IllegalStateException("retry")) : Mono.just("ok"),
                ignored -> new NormalizedUsage(4L, 2L, null, null, null, null, null, null),
                ignored -> "actual")
            .retryWhen(Retry.max(1))
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectNext("ok").verifyComplete();
        session.succeed(new NormalizedUsage(99L, 99L, null, null, null, null, null, null),
            "actual", 1);

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service, times(2)).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getAllValues()).extracting(UsageExecutionRecord::attemptIndex)
            .containsExactly(0, 1);
        assertThat(execution.getAllValues()).extracting(UsageExecutionRecord::status)
            .containsExactly(UsageStatus.FAILED, UsageStatus.SUCCEEDED);
        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(service).finishCall(org.mockito.ArgumentMatchers.any(), terminal.capture());
        assertThat(terminal.getValue().attemptCount()).isEqualTo(2);
        assertThat(terminal.getValue().usage().accountedTotalTokens()).isEqualTo(6L);
        assertThat(terminal.getValue().usage().quality()).isEqualTo(UsageQuality.PARTIAL);
    }

    @Test
    void recordsEveryAttemptWhenRetriesAreExhausted() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.RERANK, 0,
                () -> Mono.error(new IllegalStateException("unavailable")),
                ignored -> NormalizedUsage.missing(), ignored -> null)
            .retryWhen(Retry.max(2))
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectError().verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service, times(3)).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getAllValues()).extracting(UsageExecutionRecord::attemptIndex)
            .containsExactly(0, 1, 2);
        assertThat(execution.getAllValues()).extracting(UsageExecutionRecord::status)
            .containsOnly(UsageStatus.FAILED);
        assertThat(execution.getAllValues()).extracting(value -> value.usage().quality())
            .containsOnly(UsageQuality.MISSING);
    }

    @Test
    void retainsUsageCarriedByAFailedProviderAttempt() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var failure = new UsageReportingException(LanguageModelUsage.builder()
            .inputTokens(7).outputTokens(3).totalTokens(10).build());
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.GENERATION_STEP, 0,
                () -> Mono.error(failure), ignored -> NormalizedUsage.missing(), ignored -> null)
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectErrorSatisfies(error -> assertThat(error)
            .isSameAs(failure)).verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().usage().accountedTotalTokens()).isEqualTo(10L);
        assertThat(execution.getValue().usage().quality())
            .isEqualTo(UsageQuality.REPORTED_COMPONENTS);
    }

    @Test
    void recordsSynchronousInvocationFailureAsAnExecution() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.RERANK, 0,
                () -> {
                    throw new IllegalStateException("provider creation failed");
                },
                ignored -> NormalizedUsage.missing(), ignored -> null)
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectError(IllegalStateException.class).verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.FAILED);
    }

    @Test
    void classifiesWrappedTimeoutConsistently() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var timeout = reactor.core.Exceptions.propagate(new TimeoutException("slow provider"));

        session.beginExecution(UsageUnitKind.RERANK, 0)
            .fail(timeout, NormalizedUsage.missing(), null);
        session.fail(timeout, NormalizedUsage.missing(), 1);

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.TIMED_OUT);
        assertThat(execution.getValue().error().type()).isEqualTo("TIMEOUT");
        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(service).finishCall(org.mockito.ArgumentMatchers.any(), terminal.capture());
        assertThat(terminal.getValue().status()).isEqualTo(UsageStatus.TIMED_OUT);
        assertThat(terminal.getValue().error().type()).isEqualTo("TIMEOUT");
    }

    @Test
    void classifiesCancellationExceptionsConsistently() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var cancelled = new AiGenerationCancelledException("cancelled by request token");

        session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .fail(cancelled, NormalizedUsage.missing(), null);
        session.fail(cancelled, NormalizedUsage.missing(), 1);

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.CANCELLED);
        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(service).finishCall(org.mockito.ArgumentMatchers.any(), terminal.capture());
        assertThat(terminal.getValue().status()).isEqualTo(UsageStatus.CANCELLED);
    }

    @Test
    void totalTimeoutCancellationRecordsTimedOutExecution() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var timeout = Duration.ofMillis(20);
        var observed = new UsageExecutionObserver().observe(UsageUnitKind.GENERATION_STEP, 0,
                Mono::<String>never, ignored -> NormalizedUsage.missing(), ignored -> null)
            .timeout(timeout)
            .contextWrite(context -> UsageExecutionObserver.withTimeoutDeadline(context, timeout))
            .onErrorResume(TimeoutException.class, ignored -> Mono.empty())
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).verifyComplete();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.TIMED_OUT);
    }

    @Test
    void preservesUsageObservedBeforeFailedStreamAttempt() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observeFlux(UsageUnitKind.GENERATION_STEP, 0,
                () -> Flux.concat(Flux.just("partial"),
                    Flux.error(new IllegalStateException("stream failed"))),
                ignored -> new NormalizedUsage(3L, 2L, null, null, null, null, null, null),
                ignored -> "actual")
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectNext("partial").expectError().verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.FAILED);
        assertThat(execution.getValue().usage().accountedTotalTokens()).isEqualTo(5L);
        assertThat(execution.getValue().responseModelId()).isEqualTo("actual");
    }

    @Test
    void failedFinalStreamExecutionMarksLogicalCallFailed() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var failure = new IllegalStateException("stream failed");
        session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .fail(failure, NormalizedUsage.missing(), null);

        session.succeed(NormalizedUsage.missing(), null, 0);

        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(service).finishCall(org.mockito.ArgumentMatchers.any(), terminal.capture());
        assertThat(terminal.getValue().status()).isEqualTo(UsageStatus.FAILED);
        assertThat(terminal.getValue().error().type()).isEqualTo("ILLEGALSTATEEXCEPTION");
    }

    @Test
    void streamingProjectionCompletionUsesObservedStepAndResponseModel() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .succeed(NormalizedUsage.missing(), "actual-0");
        session.beginExecution(UsageUnitKind.GENERATION_STEP, 1)
            .succeed(NormalizedUsage.missing(), "actual-1");

        session.succeed(NormalizedUsage.missing(), null, 0);

        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        verify(service).finishCall(org.mockito.ArgumentMatchers.any(), terminal.capture());
        assertThat(terminal.getValue().stepCount()).isEqualTo(2);
        assertThat(terminal.getValue().responseModelId()).isEqualTo("actual-1");
    }

    @Test
    void preservesStepAndBatchIdentityWhenCompletionOrderDiffers() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observer = new UsageExecutionObserver();
        var firstStep = observer.observe(UsageUnitKind.GENERATION_STEP, 0,
            () -> Mono.just("step-0"), ignored -> NormalizedUsage.missing(), ignored -> null);
        var secondStep = observer.observe(UsageUnitKind.GENERATION_STEP, 1,
            () -> Mono.just("step-1"), ignored -> NormalizedUsage.missing(), ignored -> null);
        var slowFirstBatch = observer.observe(UsageUnitKind.EMBEDDING_BATCH, 0,
            () -> Mono.delay(Duration.ofMillis(20)).thenReturn("batch-0"),
            ignored -> NormalizedUsage.missing(), ignored -> null);
        var fastSecondBatch = observer.observe(UsageUnitKind.EMBEDDING_BATCH, 1,
            () -> Mono.just("batch-1"), ignored -> NormalizedUsage.missing(), ignored -> null);
        var observed = Flux.concat(firstStep, secondStep)
            .concatWith(Flux.merge(slowFirstBatch, fastSecondBatch))
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed)
            .expectNext("step-0", "step-1", "batch-1", "batch-0")
            .verifyComplete();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service, times(4)).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getAllValues())
            .extracting(value -> List.of(value.unitKind(), value.unitIndex(), value.attemptIndex()))
            .containsExactly(
                List.of(UsageUnitKind.GENERATION_STEP, 0, 0),
                List.of(UsageUnitKind.GENERATION_STEP, 1, 0),
                List.of(UsageUnitKind.EMBEDDING_BATCH, 1, 0),
                List.of(UsageUnitKind.EMBEDDING_BATCH, 0, 0));
    }

    @Test
    void concurrentTerminalSignalsFinalizeOnce() throws Exception {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    session.succeed(NormalizedUsage.missing(), null, 1);
                    return null;
                });
            }
            ready.await();
            start.countDown();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        verify(service, times(1)).finishCall(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void streamCancellationRecordsCancelledExecution() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observeFlux(UsageUnitKind.GENERATION_STEP, 0,
                Flux::<String>never, ignored -> NormalizedUsage.missing(), ignored -> null)
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).thenCancel().verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.CANCELLED);
    }

    @Test
    void streamCancellationPreservesLastObservedUsage() {
        var service = mock(UsageStatisticsService.class);
        var session = session(service);
        var observed = new UsageExecutionObserver().observeFlux(UsageUnitKind.GENERATION_STEP, 0,
                () -> Flux.concat(Flux.just("partial"), Flux.never()),
                ignored -> new NormalizedUsage(3L, 2L, null, null, null, null, null, null),
                ignored -> "actual")
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));

        StepVerifier.create(observed).expectNext("partial").thenCancel().verify();

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(service).recordExecution(org.mockito.ArgumentMatchers.any(), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.CANCELLED);
        assertThat(execution.getValue().usage().accountedTotalTokens()).isEqualTo(5L);
        assertThat(execution.getValue().responseModelId()).isEqualTo("actual");
    }

    private static UsageCallSession session(UsageStatisticsService service) {
        var start = new UsageCallStart("call-atomic", 1,
            Instant.parse("2026-08-11T00:00:00Z"), "plugin", "1", "stack", null,
            "language.streamText", "LANGUAGE", "model", "provider", "openai", "requested",
            true);
        return new UsageCallSession(service, start,
            Clock.fixed(Instant.parse("2026-08-11T00:00:01Z"), ZoneOffset.UTC));
    }

    private static final class UsageReportingException extends RuntimeException {
        private final LanguageModelUsage usage;

        private UsageReportingException(LanguageModelUsage usage) {
            this.usage = usage;
        }

        public LanguageModelUsage getUsage() {
            return usage;
        }
    }
}
