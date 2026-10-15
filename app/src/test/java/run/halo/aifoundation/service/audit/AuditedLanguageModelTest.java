package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.chat.middleware.GenerateTextNext;
import run.halo.aifoundation.chat.middleware.LanguageModelGenerateContext;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.aifoundation.service.language.stream.CancellableStreamReplayCoordinator;

class AuditedLanguageModelTest {

    private final CallerPluginAuditRecorder auditRecorder = mock(CallerPluginAuditRecorder.class);
    private final LanguageModel delegate = mock(LanguageModel.class);
    private final ModelCallContext context = new ModelCallContext(
        ModelType.LANGUAGE,
        "default-language",
        "openai-provider",
        "openai",
        "gpt-4"
    );
    private final UsageStatisticsService statistics = mock(UsageStatisticsService.class);
    private final AuditedLanguageModel model = new AuditedLanguageModel(delegate, context,
        auditRecorder, statistics);

    @Test
    void telemetryLinkageFailureDoesNotChangeResultOrSubscribeTwice() {
        var subscriptions = new java.util.concurrent.atomic.AtomicInteger();
        var expected = GenerateTextResult.builder().text("ok").build();
        when(delegate.generateText("hello")).thenReturn(Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return Mono.just(expected);
        }));
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any()))
            .thenThrow(new NoClassDefFoundError("unavailable telemetry dependency"));

        var result = model.generateText("hello");
        verify(delegate).generateText("hello");
        assertThat(subscriptions.get()).isZero();
        StepVerifier.create(result).expectNext(expected).verifyComplete();
        StepVerifier.create(result).expectNext(expected).verifyComplete();
        assertThat(subscriptions.get()).isEqualTo(2);
        verify(delegate).generateText("hello");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void telemetryFallbackPreservesCachedPublisher(boolean describeFails) {
        var descriptor = mock(UsageCallDescriptor.class);
        if (describeFails) {
            when(statistics.describeCall(context, "language.generateText", false, null))
                .thenThrow(new IllegalStateException("description unavailable"));
        } else {
            when(statistics.describeCall(context, "language.generateText", false, null))
                .thenReturn(descriptor);
            when(statistics.beginCall(descriptor)).thenReturn(null);
        }
        var executions = new AtomicInteger();
        var expected = GenerateTextResult.builder().text("cached").build();
        when(delegate.generateText("hello")).thenReturn(Mono.fromSupplier(() -> {
            executions.incrementAndGet();
            return expected;
        }).cache());

        var result = model.generateText("hello");

        verify(delegate).generateText("hello");
        assertThat(executions).hasValue(0);
        verify(statistics, never()).beginCall(any());
        StepVerifier.create(result).expectNext(expected).verifyComplete();
        StepVerifier.create(result).expectNext(expected).verifyComplete();
        verify(delegate).generateText("hello");
        assertThat(executions).hasValue(1);
        verify(statistics, times(describeFails ? 0 : 2)).beginCall(any());
    }

    @Test
    void failedTerminalObservationPreservesOriginalProviderError() {
        var session = mock(UsageCallSession.class);
        var providerError = new IllegalStateException("provider failure");
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any())).thenReturn(session);
        org.mockito.Mockito.doThrow(new IllegalStateException("broken telemetry"))
            .when(session).fail(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
        when(delegate.generateText("hello")).thenReturn(Mono.error(providerError));

        StepVerifier.create(model.generateText("hello"))
            .expectErrorMatches(error -> error == providerError).verify();
    }

    @Test
    void generateTextRecordsModelInvocation() {
        var result = Mono.just(GenerateTextResult.builder().text("ok").build());
        when(delegate.generateText("hello")).thenReturn(result);
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(UsageCallSession.class));

        assertThat(model.generateText("hello").block()).isSameAs(result.block());

        verify(auditRecorder).recordModelInvocation(context, "language.generateText");
        verify(delegate).generateText("hello");
    }

    @Test
    void streamTextRecordsModelInvocationForUiMessageFlow() {
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var result = new StreamTextResult(Flux.empty(), Flux.empty(), Flux.empty(), Flux.empty(),
            Mono.empty(), Mono.empty());
        when(delegate.streamText(request)).thenReturn(result);
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(mock(UsageCallDescriptor.class));

        assertThat(model.streamText(request)).isNotNull();

        verify(auditRecorder).recordModelInvocation(context, "language.streamText");
        verify(delegate).streamText(request);
    }

    @Test
    void nullStreamRequestPreservesMiddlewareEmptyRequestBehavior() {
        var expected = GenerateTextResult.builder().text("ok").build();
        var result = new StreamTextResult(Flux.empty(), Flux.just("ok"), Flux.empty(),
            Flux.empty(), Mono.empty(), Mono.just(expected));
        when(delegate.streamText(any(GenerateTextRequest.class))).thenReturn(result);
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);

        var stream = model.streamText(null);

        var request = org.mockito.ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(delegate).streamText(request.capture());
        assertThat(request.getValue()).usingRecursiveComparison()
            .isEqualTo(GenerateTextRequest.builder().build());
        verify(statistics, never()).beginCall(descriptor);
        StepVerifier.create(stream.textStream()).expectNext("ok").verifyComplete();
        StepVerifier.create(stream.result()).expectNext(expected).verifyComplete();
        verify(statistics).beginCall(descriptor);
        verify(auditRecorder).recordModelInvocation(context, "language.streamText");
    }

    @Test
    void streamProjectionsDoNotRetryDeclinedUsageAdmission() {
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var expected = GenerateTextResult.builder().text("ok").build();
        when(delegate.streamText(request)).thenReturn(new StreamTextResult(
            Flux.empty(), Flux.just("ok"), Flux.empty(), Flux.empty(), Mono.empty(),
            Mono.just(expected)));
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(null);

        var stream = model.streamText(request);

        StepVerifier.create(stream.textStream()).expectNext("ok").verifyComplete();
        StepVerifier.create(stream.result()).expectNext(expected).verifyComplete();
        verify(statistics).beginCall(descriptor);
    }

    @Test
    void streamProjectionsDoNotRetryFailedUsageAdmission() {
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var expected = GenerateTextResult.builder().text("ok").build();
        when(delegate.streamText(request)).thenReturn(new StreamTextResult(
            Flux.empty(), Flux.just("ok"), Flux.empty(), Flux.empty(), Mono.empty(),
            Mono.just(expected)));
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor))
            .thenThrow(new IllegalStateException("usage admission unavailable"));

        var stream = model.streamText(request);

        StepVerifier.create(stream.textStream()).expectNext("ok").verifyComplete();
        StepVerifier.create(stream.result()).expectNext(expected).verifyComplete();
        verify(statistics).beginCall(descriptor);
    }

    @Test
    void capabilitiesDoesNotRecordModelInvocation() {
        var capabilities = LanguageModelCapabilities.defaults();
        when(delegate.capabilities()).thenReturn(capabilities);

        assertThat(model.capabilities()).isSameAs(capabilities);

        verify(delegate).capabilities();
    }

    @Test
    void createsNoLogicalCallUntilSubscribedAndOnePerColdSubscription() {
        var statistics = mock(UsageStatisticsService.class);
        var descriptor = mock(UsageCallDescriptor.class);
        var firstSession = mock(UsageCallSession.class);
        var secondSession = mock(UsageCallSession.class);
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(firstSession, secondSession);
        when(delegate.generateText("hello"))
            .thenReturn(Mono.just(GenerateTextResult.builder().text("ok").build()));
        var instrumented = new AuditedLanguageModel(delegate, context, auditRecorder, statistics);

        var result = instrumented.generateText("hello");
        verify(statistics, never()).beginCall(descriptor);

        result.block();
        result.block();

        verify(statistics, times(2)).beginCall(descriptor);
        verify(firstSession).succeed(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1));
        verify(secondSession).succeed(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    void emptyGenerateResultDoesNotBecomeAnError() {
        var statistics = mock(UsageStatisticsService.class);
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        when(delegate.generateText("hello")).thenReturn(Mono.empty());
        var instrumented = new AuditedLanguageModel(delegate, context, auditRecorder, statistics);

        StepVerifier.create(instrumented.generateText("hello")).verifyComplete();

        verify(session).succeed(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousInvocationFailureIsThrownBeforeSubscription(boolean enabled) {
        var descriptor = mock(UsageCallDescriptor.class);
        var failure = new IllegalStateException("failed before publisher creation");
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(enabled ? descriptor : null);
        when(delegate.generateText("hello")).thenThrow(failure);

        assertThatThrownBy(() -> model.generateText("hello")).isSameAs(failure);

        verify(statistics, never()).beginCall(any());
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,true,false", "true,false,true", "true,false,false",
        "false,true,true", "false,true,false", "false,false,true", "false,false,false"})
    void assemblesMiddlewareOnceAndPreservesItsPublisher(boolean enabled, boolean cached,
        boolean requestMiddleware) {
        var descriptor = mock(UsageCallDescriptor.class);
        var firstSession = mock(UsageCallSession.class);
        var secondSession = mock(UsageCallSession.class);
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(enabled ? descriptor : null);
        when(statistics.beginCall(descriptor)).thenReturn(firstSession, secondSession);
        var wraps = new AtomicInteger();
        var executions = new AtomicInteger();
        var observedSessions = new ArrayList<UsageCallSession>();
        var expected = GenerateTextResult.builder().text("ok")
            .totalUsage(LanguageModelUsage.builder().inputTokens(10).outputTokens(5)
                .totalTokens(15).build()).build();
        when(delegate.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.deferContextual(contextView -> {
                executions.incrementAndGet();
                observedSessions.add(UsageCallSession.from(contextView));
                return Mono.just(expected);
            }));
        var middleware = new LanguageModelMiddleware() {
            @Override
            public Mono<GenerateTextResult> wrapGenerate(LanguageModelGenerateContext context,
                GenerateTextNext next) {
                wraps.incrementAndGet();
                var source = next.generate(context.request());
                return cached ? source.cache() : source;
            }
        };
        Mono<GenerateTextResult> result;
        if (requestMiddleware) {
            result = model.generateText(GenerateTextRequest.builder().prompt("hello")
                .middleware(middleware).build());
        } else {
            var wrapped = LanguageModelMiddlewares.wrap(delegate, middleware);
            result = new AuditedLanguageModel(wrapped, context, auditRecorder, statistics)
                .generateText("hello");
        }

        assertThat(wraps).hasValue(1);
        verify(delegate).generateText(any(GenerateTextRequest.class));
        assertThat(executions).hasValue(0);
        verify(statistics, never()).beginCall(any());

        StepVerifier.create(result).expectNext(expected).verifyComplete();
        StepVerifier.create(result).expectNext(expected).verifyComplete();

        assertThat(wraps).hasValue(1);
        verify(delegate).generateText(any(GenerateTextRequest.class));
        assertThat(executions).hasValue(cached ? 1 : 2);
        if (enabled) {
            verify(statistics, times(2)).beginCall(descriptor);
            verify(firstSession).succeed(isNull(), eq(1));
            verify(secondSession).succeed(isNull(), eq(1));
            assertThat(observedSessions).containsExactlyElementsOf(
                cached ? List.of(firstSession) : List.of(firstSession, secondSession));
        } else {
            verify(statistics, never()).beginCall(any());
            assertThat(observedSessions).containsOnlyNulls();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousMiddlewareFailureIsThrownBeforeSubscription(boolean enabled) {
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(enabled ? descriptor : null);
        var failure = new IllegalStateException("middleware assembly failed");
        var middleware = new LanguageModelMiddleware() {
            @Override
            public Mono<GenerateTextResult> wrapGenerate(LanguageModelGenerateContext context,
                GenerateTextNext next) {
                throw failure;
            }
        };
        var request = GenerateTextRequest.builder().prompt("hello")
            .middleware(middleware).build();

        assertThatThrownBy(() -> model.generateText(request)).isSameAs(failure);

        verify(statistics, never()).beginCall(any());
        verify(delegate, never()).generateText(any(GenerateTextRequest.class));
    }

    @Test
    void lazyMiddlewareRetryRetainsSubscriptionSessionAndResult() {
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        var attempts = new AtomicInteger();
        var observedSessions = new ArrayList<UsageCallSession>();
        var expected = GenerateTextResult.builder().text("ok")
            .totalUsage(LanguageModelUsage.builder().inputTokens(10).outputTokens(5)
                .totalTokens(15).build()).build();
        when(delegate.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.deferContextual(contextView -> {
                observedSessions.add(UsageCallSession.from(contextView));
                return attempts.incrementAndGet() == 1
                    ? Mono.error(new IllegalStateException("retry")) : Mono.just(expected);
            }));
        var middleware = new LanguageModelMiddleware() {
            @Override
            public Mono<GenerateTextResult> wrapGenerate(LanguageModelGenerateContext context,
                GenerateTextNext next) {
                return LanguageModelMiddleware.super.wrapGenerate(context, next).retry(1);
            }
        };
        var result = model.generateText(GenerateTextRequest.builder().prompt("hello")
            .middleware(middleware).build());

        verify(delegate, never()).generateText(any(GenerateTextRequest.class));
        verify(statistics, never()).beginCall(any());
        StepVerifier.create(result).expectNext(expected).verifyComplete();

        assertThat(attempts).hasValue(2);
        assertThat(observedSessions).containsExactly(session, session);
        verify(statistics).beginCall(descriptor);
        verify(session).succeed(isNull(), eq(1));
        verify(session, never()).fail(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void streamTextPropagatesUsageSessionThroughReplayCoordinator() {
        var statistics = mock(UsageStatisticsService.class);
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        var observedSession = new AtomicReference<UsageCallSession>();
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var source = Flux.deferContextual(contextView -> {
            observedSession.set(UsageCallSession.from(contextView));
            return Flux.just("ok");
        });
        var replay = new CancellableStreamReplayCoordinator<>(source, () -> "cancelled");
        var result = new StreamTextResult(Flux.empty(), replay.flux(), Flux.empty(), Flux.empty(),
            Mono.empty(), Mono.never());
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        when(delegate.streamText(request)).thenReturn(result);
        var instrumented = new AuditedLanguageModel(delegate, context, auditRecorder, statistics);

        assertThat(instrumented.streamText(request).textStream().collectList().block())
            .containsExactly("ok");

        assertThat(observedSession.get()).isSameAs(session);
    }

    @Test
    void cancellingOneActiveProjectionDoesNotCancelTheSharedCall() {
        var statistics = mock(UsageStatisticsService.class);
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var result = new StreamTextResult(Flux.never(), Flux.never(), Flux.never(), Flux.never(),
            Mono.never(), Mono.never());
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        when(delegate.streamText(request)).thenReturn(result);
        var instrumented = new AuditedLanguageModel(delegate, context, auditRecorder, statistics);
        var audited = instrumented.streamText(request);

        var fullSubscription = audited.fullStream().subscribe();
        var textSubscription = audited.textStream().subscribe();
        fullSubscription.dispose();

        verify(session, never()).cancel();

        textSubscription.dispose();
        verify(session).cancel();
    }

    @Test
    void inapplicableStructuredProjectionDoesNotCreateALogicalCall() {
        var statistics = mock(UsageStatisticsService.class);
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        var request = GenerateTextRequest.builder().prompt("hello").build();
        var result = new StreamTextResult(Flux.empty(), Flux.empty(), Flux.empty(), Flux.empty(),
            Mono.empty(), Mono.empty());
        when(statistics.describeCall(context, "language.streamText", true, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        when(delegate.streamText(request)).thenReturn(result);
        var instrumented = new AuditedLanguageModel(delegate, context, auditRecorder, statistics);

        StepVerifier.create(instrumented.streamText(request).output()).verifyComplete();

        StepVerifier.create(instrumented.streamText(request).partialOutputStream())
            .verifyComplete();
        StepVerifier.create(instrumented.streamText(request).elementStream()).verifyComplete();

        verify(statistics, never()).beginCall(descriptor);
        verify(session, never()).succeed(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt());
    }
}
