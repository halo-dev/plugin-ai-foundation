package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageExecutionScope;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageUnitKind;
import run.halo.aifoundation.tool.ToolDefinition;

class UsageLanguageRegressionTest {

    @Test
    void reasoningToolAggregationRetainsUsageOnlyChunk() {
        var chat = mock(ChatModel.class);
        var answer = new ChatResponse(List.of(new Generation(new AssistantMessage("answer"),
            ChatGenerationMetadata.builder().finishReason("stop").build())));
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.just(answer, usage()));
        var session = mock(UsageCallSession.class);
        var scope = mock(UsageExecutionScope.class);
        when(session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)).thenReturn(scope);

        var result = model(chat).generateText(request())
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session))
            .block();

        assertThat(result).isNotNull();
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(15);
        verify(chat).stream(any(Prompt.class));
        verify(scope).succeed(argThat(value -> value.accountedTotalTokens() == 15L),
            nullable(String.class));
    }

    @Test
    void reasoningToolFailureRetainsAlreadyReportedUsageAndOriginalError() {
        var chat = mock(ChatModel.class);
        var failure = new IllegalStateException("provider failed");
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.just(usage())
            .concatWith(Flux.error(failure)));
        var session = mock(UsageCallSession.class);
        var scope = mock(UsageExecutionScope.class);
        when(session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)).thenReturn(scope);

        StepVerifier.create(model(chat).generateText(request())
                .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session)))
            .expectErrorMatches(error -> error == failure)
            .verify();

        verify(scope).fail(eq(failure), argThat(value -> value.accountedTotalTokens() == 15L),
            eq("test-model"));
        verify(chat).stream(any(Prompt.class));
    }

    @Test
    void reasoningToolCancellationRetainsAlreadyReportedUsage() {
        var chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.just(usage())
            .concatWith(Flux.never()));
        var session = mock(UsageCallSession.class);
        var scope = mock(UsageExecutionScope.class);
        when(session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)).thenReturn(scope);
        var subscription = model(chat).generateText(request())
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session))
            .subscribe();
        subscription.dispose();

        verify(scope).cancel(argThat(value -> value.accountedTotalTokens() == 15L), eq("test-model"));
        verify(chat).stream(any(Prompt.class));
    }

    @Test
    void reasoningToolTimeoutRetainsAlreadyReportedUsage() {
        var chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.just(usage())
            .concatWith(Flux.never()));
        var session = mock(UsageCallSession.class);
        var scope = mock(UsageExecutionScope.class);
        when(session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)).thenReturn(scope);
        var timedRequest = GenerateTextRequest.builder().prompt("hello").maxRetries(0)
            .tools(List.of(ToolDefinition.builder().name("test").build()))
            .timeouts(run.halo.aifoundation.chat.GenerationTimeouts.builder()
                .stepTimeout(java.time.Duration.ofMillis(100)).build()).build();

        StepVerifier.withVirtualTime(() -> model(chat).generateText(timedRequest)
                .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session)))
            .thenAwait(java.time.Duration.ofSeconds(1))
            .expectError(run.halo.aifoundation.exception.AiGenerationTimeoutException.class)
            .verify();

        verify(scope).fail(any(run.halo.aifoundation.exception.AiGenerationTimeoutException.class),
            argThat(value -> value.accountedTotalTokens() == 15L), eq("test-model"));
        verify(chat).stream(any(Prompt.class));
    }

    @Test
    void reasoningToolContinuousChunksDoNotExtendStepDeadline() {
        var chat = mock(ChatModel.class);
        var answer = new ChatResponse(List.of(new Generation(new AssistantMessage("answer"),
            ChatGenerationMetadata.builder().finishReason("stop").build())));
        when(chat.stream(any(Prompt.class))).thenAnswer(ignored -> Flux.just(usage())
            .concatWith(Flux.interval(Duration.ofMillis(200)).take(20).map(index -> answer)));
        var observation = mock(UsageObservation.class);
        var start = new UsageCallStart("call", 1, Instant.EPOCH, "plugin", "1", "test", null,
            "language.generateText", "LANGUAGE", "model", "provider", "test", "test-model", false);
        var session = new UsageCallSession(observation, start, Clock.systemUTC());
        var timeout = Duration.ofSeconds(1);
        var timedRequest = GenerateTextRequest.builder().prompt("hello").maxRetries(2)
            .tools(List.of(ToolDefinition.builder().name("test").build()))
            .timeouts(GenerationTimeouts.builder().stepTimeout(timeout).build()).build();

        StepVerifier.withVirtualTime(() -> model(chat).generateText(timedRequest)
                .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session)))
            .expectSubscription()
            .expectNoEvent(Duration.ofMillis(999))
            .thenAwait(Duration.ofMillis(1))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(AiGenerationTimeoutException.class);
                var failure = (AiGenerationTimeoutException) error;
                assertThat(failure.getScope()).isEqualTo("step");
                assertThat(failure.getTimeout()).isEqualTo(timeout);
            })
            .verify(Duration.ofSeconds(5));

        var execution = ArgumentCaptor.forClass(UsageExecutionRecord.class);
        verify(observation).recordExecution(eq(session), execution.capture());
        assertThat(execution.getValue().status()).isEqualTo(UsageStatus.TIMED_OUT);
        assertThat(execution.getValue().error().type()).isEqualTo("TIMEOUT");
        assertThat(execution.getValue().usage().inputTokens()).isEqualTo(10L);
        assertThat(execution.getValue().usage().outputTokens()).isEqualTo(5L);
        assertThat(execution.getValue().usage().accountedTotalTokens()).isEqualTo(15L);
        assertThat(execution.getValue().responseModelId()).isEqualTo("test-model");
        verify(chat).stream(any(Prompt.class));
    }

    @Test
    void reasoningToolStreamCanCompleteBeforeStepDeadline() {
        var chat = mock(ChatModel.class);
        var answer = new ChatResponse(List.of(new Generation(new AssistantMessage("answer"),
            ChatGenerationMetadata.builder().finishReason("stop").build())));
        when(chat.stream(any(Prompt.class))).thenAnswer(ignored -> Flux.just(answer, usage())
            .delayElements(Duration.ofMillis(200)));
        var timedRequest = GenerateTextRequest.builder().prompt("hello").maxRetries(0)
            .tools(List.of(ToolDefinition.builder().name("test").build()))
            .timeouts(GenerationTimeouts.builder().stepTimeout(Duration.ofSeconds(1)).build())
            .build();

        StepVerifier.withVirtualTime(() -> model(chat).generateText(timedRequest))
            .thenAwait(Duration.ofMillis(400))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("answer");
                assertThat(result.getUsage().getTotalTokens()).isEqualTo(15);
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));
        verify(chat).stream(any(Prompt.class));
    }

    @Test
    void preCancelledSimpleStreamPreservesCancellation() {
        var session = mock(UsageCallSession.class);
        var chat = mock(ChatModel.class);
        var source = new CancellationSource();
        source.cancel();
        var request = GenerateTextRequest.builder().prompt("hello")
            .cancellationToken(source.token()).build();

        new LanguageModelImpl(chat, "openai").streamText(request).textStream()
            .contextWrite(context -> context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session))
            .collectList().block();

        verify(session).fail(any(AiGenerationCancelledException.class), any(NormalizedUsage.class), eq(0));
        verifyNoInteractions(chat);
    }

    private static ChatResponse usage() {
        return new ChatResponse(List.of(), ChatResponseMetadata.builder().model("test-model")
            .usage(new DefaultUsage(10, 5)).build());
    }

    private static GenerateTextRequest request() {
        return GenerateTextRequest.builder().prompt("hello").maxRetries(0)
            .tools(List.of(ToolDefinition.builder().name("test").build())).build();
    }

    private static LanguageModelImpl model(ChatModel chat) {
        var options = LanguageModelProviderOptions.builder().reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true).build();
        var composition = LanguageModelRuntimeComposition.create("test", options,
            new LanguageModelRuntimeSupport());
        return new LanguageModelImpl(chat, composition, ModelRuntimeContext.unresolved("test"),
            10, new UsageExecutionObserver());
    }
}
