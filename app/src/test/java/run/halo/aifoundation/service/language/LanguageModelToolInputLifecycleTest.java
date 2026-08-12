package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamingChatModel;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolDefinition;

class LanguageModelToolInputLifecycleTest extends LanguageModelTestSupport {

    @Test
    void streamsBackpressuredLifecycleInCanonicalOrderAndOnlyOnceAcrossViews() {
        var chatModel = providerStreamingModel();
        var provider = (ProviderStreamingChatModel) chatModel;
        when(provider.streamParts(any(Prompt.class))).thenReturn(
            Flux.just(
                new ProviderStreamPart.ToolInputStartPart(0, "call_1", "weather"),
                new ProviderStreamPart.ToolInputDeltaPart(0, "{\"location\":"),
                new ProviderStreamPart.ToolInputDeltaPart(0, "\"SF\"}"),
                new ProviderStreamPart.ToolInputEndPart(0),
                new ProviderStreamPart.ChatResponsePart(
                    toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3))
            ),
            Flux.just(new ProviderStreamPart.ChatResponsePart(
                chatResponse("It is 22C.", "stop", 4, 5)))
        );
        var events = new ArrayList<String>();
        var callbackCount = new AtomicInteger();
        var executorCount = new AtomicInteger();
        var tool = ToolDefinition.builder()
            .name("weather")
            .inputSchema(weatherInputSchema())
            .onInputStart(context -> Mono.fromRunnable(() -> {
                callbackCount.incrementAndGet();
                events.add("callback:start");
            }))
            .onInputDelta(context -> Mono.fromRunnable(() -> {
                callbackCount.incrementAndGet();
                events.add("callback:delta:" + context.getInputTextDelta());
            }))
            .onInputAvailable(context -> Mono.fromRunnable(() -> {
                callbackCount.incrementAndGet();
                assertThat(context.getInput()).containsEntry("location", "SF");
                events.add("callback:available");
            }))
            .executor(context -> Mono.fromSupplier(() -> {
                executorCount.incrementAndGet();
                events.add("executor");
                return Map.of("temperature", 22);
            }))
            .build();
        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(tool))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        var stream = languageModel(chatModel, "openai").streamText(request);

        StepVerifier.create(stream.fullStream()
                .doOnNext(part -> events.add("part:" + part.getType()))
                .collectList())
            .assertNext(parts -> assertThat(parts).extracting(TextStreamPart::getType)
                .containsSubsequence(
                    PartType.TOOL_INPUT_START,
                    PartType.TOOL_INPUT_DELTA,
                    PartType.TOOL_INPUT_DELTA,
                    PartType.TOOL_INPUT_END,
                    PartType.TOOL_CALL,
                    PartType.TOOL_RESULT
                ))
            .verifyComplete();
        StepVerifier.create(stream.result())
            .assertNext(result -> assertThat(result.getText()).isEqualTo("It is 22C."))
            .verifyComplete();

        assertThat(events).containsSubsequence(
            "callback:start", "part:tool-input-start",
            "callback:delta:{\"location\":", "part:tool-input-delta",
            "callback:delta:\"SF\"}", "part:tool-input-delta",
            "part:tool-input-end", "part:tool-call",
            "callback:available", "executor", "part:tool-result"
        );
        assertThat(callbackCount).hasValue(4);
        assertThat(executorCount).hasValue(1);
    }

    @Test
    void doesNotPublishInputOrReadAheadWhileCallbackIsPending() {
        var chatModel = providerStreamingModel();
        var provider = (ProviderStreamingChatModel) chatModel;
        when(provider.streamParts(any(Prompt.class))).thenReturn(Flux.just(
            new ProviderStreamPart.ToolInputStartPart(0, "call_1", "weather"),
            new ProviderStreamPart.ToolInputEndPart(0),
            new ProviderStreamPart.ChatResponsePart(
                toolCallResponse("call_1", "weather", "{}", 1, 1))
        ));
        var starts = new AtomicInteger();
        var callbackGate = Sinks.<Void>empty();
        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .onInputStart(context -> Mono.fromRunnable(starts::incrementAndGet)
                    .then(callbackGate.asMono()))
                .build()))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").streamText(request).fullStream(), 0)
            .thenRequest(3)
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .then(() -> assertThat(starts).hasValue(1))
            .then(() -> callbackGate.tryEmitEmpty())
            .expectNextMatches(part -> PartType.TOOL_INPUT_START.equals(part.getType()))
            .thenCancel()
            .verify();
    }

    @Test
    void callbackFailureClosesOpenInputAndPreventsAvailabilityAndExecution() {
        var chatModel = providerStreamingModel();
        var provider = (ProviderStreamingChatModel) chatModel;
        when(provider.streamParts(any(Prompt.class))).thenReturn(Flux.just(
            new ProviderStreamPart.ToolInputStartPart(0, "call_1", "weather"),
            new ProviderStreamPart.ToolInputDeltaPart(0, "{}")
        ));
        var available = new AtomicInteger();
        var executions = new AtomicInteger();
        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .onInputDelta(context -> Mono.error(new IllegalStateException("delta rejected")))
                .onInputAvailable(context -> Mono.fromRunnable(available::incrementAndGet))
                .executor(context -> Mono.fromSupplier(() -> {
                    executions.incrementAndGet();
                    return Map.of();
                }))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").streamText(request)
                .fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsSubsequence(
                        PartType.TOOL_INPUT_START,
                        PartType.TOOL_INPUT_END,
                        PartType.ERROR
                    )
                    .doesNotContain(PartType.TOOL_INPUT_DELTA, PartType.TOOL_CALL);
                assertThat(parts.getLast().getErrorText()).contains("delta rejected");
            })
            .verifyComplete();
        assertThat(available).hasValue(0);
        assertThat(executions).hasValue(0);
    }

    @Test
    void callbackUsesStepTimeoutAndCancellationChecks() {
        var timeoutModel = providerStreamingModel();
        when(((ProviderStreamingChatModel) timeoutModel).streamParts(any(Prompt.class)))
            .thenReturn(Flux.just(
                new ProviderStreamPart.ToolInputStartPart(0, "call_1", "slow")));
        var timeoutRequest = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("slow")
                .onInputStart(context -> Mono.never())
                .build()))
            .timeouts(GenerationTimeouts.builder()
                .stepTimeout(Duration.ofMillis(25))
                .build())
            .build();

        StepVerifier.create(languageModel(timeoutModel, "openai").streamText(timeoutRequest)
                .fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts.getLast().getType()).isEqualTo(PartType.ERROR);
                assertThat(parts.getLast().getProviderMetadata()).isNullOrEmpty();
            })
            .verifyComplete();

        var cancellation = new CancellationSource();
        var cancelModel = providerStreamingModel();
        when(((ProviderStreamingChatModel) cancelModel).streamParts(any(Prompt.class)))
            .thenReturn(Flux.just(
                new ProviderStreamPart.ToolInputStartPart(0, "call_1", "cancel")));
        var cancelRequest = GenerateTextRequest.builder()
            .prompt("Use tool")
            .cancellationToken(cancellation.token())
            .tools(List.of(ToolDefinition.builder()
                .name("cancel")
                .onInputStart(context -> Mono.fromRunnable(cancellation::cancel))
                .build()))
            .build();

        StepVerifier.create(languageModel(cancelModel, "openai").streamText(cancelRequest)
                .fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts.getLast().getType()).isEqualTo(PartType.ERROR);
                assertThat(parts.getLast().getProviderMetadata()).isNullOrEmpty();
            })
            .verifyComplete();
    }

    @Test
    void nonStreamingNormalizesBeforeAvailabilityApprovalExternalHandoffAndExecution() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3));
        var events = new ArrayList<String>();
        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .onInputStart(context -> Mono.fromRunnable(() -> events.add("start")))
                .onInputDelta(context -> Mono.fromRunnable(() -> events.add("delta")))
                .onInputAvailable(context -> Mono.fromRunnable(() -> {
                    assertThat(context.getInput()).containsEntry("location", "SF");
                    events.add("available");
                }))
                .approvalPredicate(context -> {
                    assertThat(context.getInput()).containsEntry("location", "SF");
                    events.add("approval");
                    return true;
                })
                .executor(context -> Mono.fromSupplier(() -> {
                    events.add("executor");
                    return Map.of();
                }))
                .build()))
            .toolCallRepair(context -> {
                events.add("repair");
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolApprovalRequests()).hasSize(1);
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired");
            })
            .verifyComplete();
        assertThat(events).containsExactly("start", "repair", "available", "approval");
    }

    @Test
    void streamingExternalToolValidationFailureDoesNotBecomeAvailable() {
        var chatModel = providerStreamingModel();
        when(((ProviderStreamingChatModel) chatModel).streamParts(any(Prompt.class)))
            .thenReturn(Flux.just(
                new ProviderStreamPart.ToolInputStartPart(0, "call_1", "weather"),
                new ProviderStreamPart.ToolInputDeltaPart(0, "{\"city\":\"SF\"}"),
                new ProviderStreamPart.ToolInputEndPart(0),
                new ProviderStreamPart.ChatResponsePart(
                    toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3))
            ));
        var available = new AtomicInteger();
        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .onInputAvailable(context -> Mono.fromRunnable(available::incrementAndGet))
                .build()))
            .toolCallRepair(context -> Mono.just(ToolCallRepairResult.unrepaired()))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").streamText(request)
                .fullStream().collectList())
            .assertNext(parts -> assertThat(parts).extracting(TextStreamPart::getType)
                .containsSubsequence(PartType.TOOL_INPUT_END, PartType.TOOL_INPUT_ERROR)
                .doesNotContain(PartType.TOOL_CALL))
            .verifyComplete();
        assertThat(available).hasValue(0);
    }

    @Test
    void serializesInterleavedToolInputCallbacksGlobally() {
        var chatModel = providerStreamingModel();
        when(((ProviderStreamingChatModel) chatModel).streamParts(any(Prompt.class)))
            .thenReturn(Flux.just(
                new ProviderStreamPart.ToolInputStartPart(0, "call_1", "first"),
                new ProviderStreamPart.ToolInputStartPart(1, "call_2", "second"),
                new ProviderStreamPart.ToolInputDeltaPart(0, "{}"),
                new ProviderStreamPart.ToolInputDeltaPart(1, "{}"),
                new ProviderStreamPart.ToolInputEndPart(0),
                new ProviderStreamPart.ToolInputEndPart(1),
                new ProviderStreamPart.ChatResponsePart(multiToolCallResponse(List.of(
                    new AssistantMessage.ToolCall("call_1", "function", "first", "{}"),
                    new AssistantMessage.ToolCall("call_2", "function", "second", "{}")
                ), 2, 3))
            ));
        var events = new ArrayList<String>();
        var first = ToolDefinition.builder()
            .name("first")
            .onInputStart(context -> Mono.fromRunnable(() -> events.add("start:first")))
            .onInputDelta(context -> Mono.fromRunnable(() -> events.add("delta:first")))
            .onInputAvailable(context -> Mono.fromRunnable(() -> events.add("available:first")))
            .build();
        var second = ToolDefinition.builder()
            .name("second")
            .onInputStart(context -> Mono.fromRunnable(() -> events.add("start:second")))
            .onInputDelta(context -> Mono.fromRunnable(() -> events.add("delta:second")))
            .onInputAvailable(context -> Mono.fromRunnable(() -> events.add("available:second")))
            .build();
        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(first, second))
            .build();

        StepVerifier.create(languageModel(chatModel, "openai").streamText(request)
                .fullStream().collectList())
            .assertNext(parts -> assertThat(parts).extracting(TextStreamPart::getType)
                .containsSubsequence(
                    PartType.TOOL_INPUT_START,
                    PartType.TOOL_INPUT_START,
                    PartType.TOOL_INPUT_DELTA,
                    PartType.TOOL_INPUT_DELTA,
                    PartType.TOOL_INPUT_END,
                    PartType.TOOL_INPUT_END,
                    PartType.TOOL_CALL,
                    PartType.TOOL_CALL
                ))
            .verifyComplete();
        assertThat(events).containsExactly(
            "start:first", "start:second", "delta:first", "delta:second",
            "available:first", "available:second"
        );
    }

    private ChatModel providerStreamingModel() {
        return mock(ChatModel.class,
            withSettings().extraInterfaces(ProviderStreamingChatModel.class));
    }
}
