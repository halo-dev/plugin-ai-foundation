package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.usage.UsageCallDescriptor;
import run.halo.aifoundation.service.usage.UsageCallSession;
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
        verify(firstSession).succeed(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1));
        verify(secondSession).succeed(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1));
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

        verify(session).succeed(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(0));
    }

    @Test
    void synchronousInvocationFailureTerminatesTheLogicalCall() {
        var descriptor = mock(UsageCallDescriptor.class);
        var session = mock(UsageCallSession.class);
        var failure = new IllegalStateException("failed before publisher creation");
        when(statistics.describeCall(context, "language.generateText", false, null))
            .thenReturn(descriptor);
        when(statistics.beginCall(descriptor)).thenReturn(session);
        when(delegate.generateText("hello")).thenThrow(failure);

        StepVerifier.create(model.generateText("hello"))
            .expectErrorMatches(error -> error == failure)
            .verify();

        verify(session).fail(org.mockito.ArgumentMatchers.same(failure),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(0));
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

        verify(statistics, times(1)).beginCall(descriptor);
        verify(session, never()).succeed(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
