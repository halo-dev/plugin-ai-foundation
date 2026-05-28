package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.lifecycle.GenerationStepFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationStepStartEvent;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.lifecycle.GenerationToolCallFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationToolCallStartEvent;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.chat.PreparedStep;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.language.stream.StreamProtocolNormalizer;

class LanguageModelImplTest {

    @Test
    void generateText_mapsMessagesAndOptionsToSpringPrompt() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 3, 5));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .system("You are concise.")
            .messages(List.of(
                ModelMessage.user("Hello"),
                ModelMessage.assistant("Hi")
            ))
            .temperature(0.2)
            .maxOutputTokens(128)
            .topP(0.9)
            .topK(40)
            .presencePenalty(0.1)
            .frequencyPenalty(0.3)
            .stopSequences(List.of("END"))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Done");
                assertThat(result.getContent())
                    .singleElement()
                    .satisfies(part -> {
                        assertThat(part.getType()).isEqualTo("text");
                        assertThat(part.getText()).isEqualTo("Done");
                    });
                assertThat(result.getFinishReason()).isEqualTo(FinishReason.STOP);
                assertThat(result.getRawFinishReason()).isEqualTo("stop");
                assertThat(result.getUsage().getInputTokens()).isEqualTo(3);
                assertThat(result.getUsage().getOutputTokens()).isEqualTo(5);
                assertThat(result.getUsage().getTotalTokens()).isEqualTo(8);
                assertThat(result.getTotalUsage().getTotalTokens()).isEqualTo(8);
                assertThat(result.getRequest().getMetadata()).containsEntry("providerType", "openai");
                assertThat(result.getResponse().getMessages())
                    .singleElement()
                    .satisfies(message -> assertThat(message.getRole())
                        .isEqualTo(run.halo.aifoundation.message.ModelMessageRole.ASSISTANT));
                assertThat(result.getSteps())
                    .singleElement()
                    .satisfies(step -> {
                        assertThat(step.getStepIndex()).isZero();
                        assertThat(step.getText()).isEqualTo("Done");
                        assertThat(step.getUsage().getTotalTokens()).isEqualTo(8);
                    });
            })
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());

        var prompt = captor.getValue();
        assertThat(prompt.getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .containsExactly("system", "user", "assistant");
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.2);
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(128);
        assertThat(prompt.getOptions().getTopP()).isEqualTo(0.9);
        assertThat(prompt.getOptions().getTopK()).isEqualTo(40);
        assertThat(prompt.getOptions().getPresencePenalty()).isEqualTo(0.1);
        assertThat(prompt.getOptions().getFrequencyPenalty()).isEqualTo(0.3);
        assertThat(prompt.getOptions().getStopSequences()).containsExactly("END");
    }

    @Test
    void generateText_rejectsPromptAndMessagesTogether() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .messages(List.of(ModelMessage.user("Hi")))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("exactly one of prompt or messages must be provided")
            .verify();
    }

    @Test
    void generateText_rejectsUnsupportedContentPart() {
        assertThatThrownBy(() -> ModelMessagePart.builder().type("image").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("unsupported message part type: image");
    }

    @Test
    void generateText_rejectsReasoningHistoryWhenProviderUnsupported() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "ollama");
        var request = GenerateTextRequest.builder()
            .messages(List.of(ModelMessage.assistant(List.of(
                ModelMessagePart.reasoning("thinking")
            ))))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("reasoning content is not supported by provider type: ollama")
            .verify();
    }

    @Test
    void generateText_mapsReasoningAndReasoningTokens() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            reasoningResponse("Think first.", "Answer.", "stop", 3, 5, 2)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Answer.");
                assertThat(result.getReasoningText()).isEqualTo("Think first.");
                assertThat(result.getReasoning())
                    .singleElement()
                    .satisfies(reasoning -> {
                        assertThat(reasoning.getText()).isEqualTo("Think first.");
                        assertThat(reasoning.getProviderMetadata().toString())
                            .contains("reasoning_content");
                    });
                assertThat(result.getContent()).extracting("type")
                    .containsExactly(PartType.REASONING, PartType.TEXT);
                assertThat(result.getUsage().getReasoningTokens()).isEqualTo(2);
                assertThat(result.getTotalUsage().getReasoningTokens()).isEqualTo(2);
                assertThat(result.getSteps().get(0).getReasoningText()).isEqualTo("Think first.");
            })
            .verifyComplete();
    }

    @Test
    void generateText_executesToolAndContinuesWhenMaxStepsAllows() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var toolContext = new AtomicReference<ToolExecutionContext>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .description("Get weather")
                .inputSchema(Map.of("type", "object"))
                .executor(context -> {
                    toolContext.set(context);
                    return reactor.core.publisher.Mono.just(Map.of(
                        "location", context.getInput().get("location"),
                        "temperature", 22
                    ));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getContent())
                    .extracting("type")
                    .contains("tool-call", "tool-result", "text");
                assertThat(result.getSteps().get(0).getToolCalls())
                    .singleElement()
                    .satisfies(call -> {
                        assertThat(call.getToolCallId()).isEqualTo("call_1");
                        assertThat(call.getToolName()).isEqualTo("weather");
                        assertThat(call.getInput()).containsEntry("location", "SF");
                    });
                assertThat(result.getSteps().get(0).getToolResults())
                    .singleElement()
                    .satisfies(toolResult -> assertThat(toolResult.getResult().toString())
                        .contains("temperature=22"));
                assertThat(result.getToolCalls()).hasSize(1);
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getToolErrors()).isEmpty();
            })
            .verifyComplete();

        assertThat(toolContext.get().getToolCallId()).isEqualTo("call_1");
        assertThat(toolContext.get().getToolName()).isEqualTo("weather");
        assertThat(toolContext.get().getStepIndex()).isZero();
        assertThat(toolContext.get().getMessages()).isNotEmpty();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .contains("tool");
    }

    @Test
    void generateText_toolContextIncludesPriorToolHistoryForLaterSteps() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            toolCallResponse("call_2", "weather", "{\"location\":\"NYC\"}", 4, 5),
            chatResponse("Done", "stop", 6, 7)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var contexts = new ArrayList<ToolExecutionContext>();

        var request = GenerateTextRequest.builder()
            .prompt("Compare weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> {
                    contexts.add(context);
                    return reactor.core.publisher.Mono.just(Map.of("ok", true));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(3))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolCalls()).hasSize(2);
                assertThat(result.getToolResults()).hasSize(2);
                assertThat(result.getText()).isEqualTo("Done");
            })
            .verifyComplete();

        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(1).getStepIndex()).isEqualTo(1);
        assertThat(contexts.get(1).getMessages())
            .extracting(ModelMessage::getRole)
            .contains(ModelMessageRole.ASSISTANT, ModelMessageRole.TOOL);
        assertThat(contexts.get(1).getMessages().stream()
            .flatMap(message -> message.getContent().stream())
            .map(ModelMessagePart::getType))
            .contains(PartType.TOOL_CALL, PartType.TOOL_RESULT);
    }

    @Test
    void generateText_preservesReasoningWhenContinuingAfterToolCall() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(reasoningToolCallResponse("call_1", "weather", "{\"location\":\"SF\"}",
                "Need weather data.", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "deepseek", reasoningToolProviderOptions());

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getSteps().get(0).getReasoningText())
                    .isEqualTo("Need weather data.");
            })
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions())
            .filteredOn(message -> "assistant".equals(message.getMessageType().getValue()))
            .singleElement()
            .satisfies(message -> assertThat(message.getMetadata())
                .containsEntry("reasoningContent", "Need weather data."));
    }

    @Test
    void generateText_deepSeekToolRequestsDoNotDisableThinkingModeByDefault() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "deepseek", reasoningToolProviderOptions());

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(1))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectNextCount(1)
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
        var options = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(options.getExtraBody()).isNullOrEmpty();
    }

    @Test
    void generateText_defaultMaxStepsDoesNotExecuteTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(1);
                assertThat(result.getSteps().get(0).getToolResults()).isEmpty();
                assertThat(result.getWarnings())
                    .extracting("code")
                    .contains("stop-condition-reached");
            })
            .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateText_stopWhenControlsToolLoopWithoutMaxSteps() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getToolResults()).hasSize(1);
            })
            .verifyComplete();

        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generateText_prepareStepLimitsActiveTools() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(
                ToolDefinition.builder()
                    .name("weather")
                    .executor(context -> reactor.core.publisher.Mono.just(Map.of()))
                    .build(),
                ToolDefinition.builder()
                    .name("search")
                    .executor(context -> reactor.core.publisher.Mono.just(Map.of()))
                    .build()
            ))
            .prepareStep(context -> PreparedStep.builder()
                .activeTools(List.of("search"))
                .build())
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getSteps().get(0).getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("Unknown tool")))
            .verifyComplete();
    }

    @Test
    void generateText_rejectsUnknownPreparedActiveTool() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder().name("weather").build()))
            .prepareStep(context -> PreparedStep.builder()
                .activeTools(List.of("missing"))
                .build())
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("prepareStep activeTools references unknown tool: missing")
            .verify();
    }

    @Test
    void generateText_recordsToolErrorForUnknownTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "unknown", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of()))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getSteps().get(0).getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("Unknown tool")))
            .verifyComplete();
    }

    @Test
    void generateText_warnsAndStopsWhenToolHasNoExecutor() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(1);
                assertThat(result.getSteps().get(0).getToolResults()).isEmpty();
                assertThat(result.getWarnings())
                    .extracting("code")
                    .contains("tool-not-executed");
            })
            .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateText_recordsToolErrorWhenExecutorFails() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.error(
                    new IllegalStateException("tool failed")))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getSteps().get(0).getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).isEqualTo("tool failed")))
            .verifyComplete();
    }

    @Test
    void generateText_rejectsInvalidToolOptions() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .tools(List.of(ToolDefinition.builder().name("bad name").build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("tool name must contain only letters, numbers, '_' or '-'")
            .verify();
    }

    @Test
    void generateText_rejectsToolRequestsWhenProviderDoesNotSupportTools() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "simple") {
            @Override
            protected boolean supportsToolCalling() {
                return false;
            }
        };
        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .tools(List.of(ToolDefinition.builder().name("weather").build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("Tool calling is not supported by provider type: simple")
            .verify();
    }

    @Test
    void streamText_emitsHaloTextStreamParts() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("Hel", null, null, null),
            chatResponse("lo", null, null, null),
            chatResponse("", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build()).fullStream())
            .assertNext(part -> assertThat(part.getType()).isEqualTo(PartType.START))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.START_STEP);
                assertThat(part.getStepIndex()).isZero();
            })
            .assertNext(part -> assertThat(part.getType()).isEqualTo(PartType.TEXT_START))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("Hel");
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("lo");
            })
            .assertNext(part -> assertThat(part.getType()).isEqualTo(PartType.TEXT_END))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FINISH_STEP);
                assertThat(part.getStepIndex()).isZero();
                assertThat(part.getFinishReason()).isEqualTo(FinishReason.STOP);
                assertThat(part.getUsage().getInputTokens()).isEqualTo(2);
                assertThat(part.getUsage().getOutputTokens()).isEqualTo(4);
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FINISH);
                assertThat(part.getFinishReason()).isEqualTo(FinishReason.STOP);
                assertThat(part.getUsage().getInputTokens()).isEqualTo(2);
                assertThat(part.getUsage().getOutputTokens()).isEqualTo(4);
            })
            .verifyComplete();
    }

    @Test
    void streamText_resultViewsShareSingleExecution() {
        var calls = new AtomicInteger();
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenAnswer(invocation -> Flux.defer(() -> {
            calls.incrementAndGet();
            return Flux.just(
                chatResponse("Hel", null, null, null),
                chatResponse("lo", "stop", 2, 4)
            );
        }));
        var model = new LanguageModelImpl(chatModel, "openai");

        var result = model.streamText(GenerateTextRequest.builder().prompt("Hi").build());

        StepVerifier.create(result.textStream())
            .expectNext("Hel", "lo")
            .verifyComplete();
        StepVerifier.create(result.result())
            .assertNext(finalResult -> assertThat(finalResult.getText()).isEqualTo("Hello"))
            .verifyComplete();
        StepVerifier.create(result.fullStream().filter(part -> PartType.FINISH.equals(part.getType())))
            .expectNextCount(1)
            .verifyComplete();

        assertThat(calls).hasValue(1);
    }

    @Test
    void streamText_exposesConvenienceResultProjections() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("Hello", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        var result = model.streamText(GenerateTextRequest.builder().prompt("Hi").build());

        StepVerifier.create(result.text())
            .expectNext("Hello")
            .verifyComplete();
        StepVerifier.create(result.finishReason())
            .expectNext(FinishReason.STOP)
            .verifyComplete();
        StepVerifier.create(result.totalUsage())
            .assertNext(usage -> assertThat(usage.getTotalTokens()).isEqualTo(6))
            .verifyComplete();
        StepVerifier.create(result.steps())
            .assertNext(steps -> assertThat(steps).hasSize(1))
            .verifyComplete();

        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_mapsSourceAndFileMetadataParts() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("See source", "stop", 2, 4, Map.of(
                "sources", List.of(Map.of("id", "src_1", "url", "https://example.com",
                    "title", "Example")),
                "files", List.of(Map.of("id", "file_1", "filename", "answer.txt",
                    "mediaType", "text/plain", "data", "SGVsbG8="))
            ))
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        var result = model.streamText(GenerateTextRequest.builder().prompt("Hi").build());

        StepVerifier.create(result.fullStream()
                .filter(part -> PartType.SOURCE.equals(part.getType())
                    || PartType.FILE.equals(part.getType())))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.SOURCE);
                assertThat(part.getUrl()).isEqualTo("https://example.com");
                assertThat(part.getTitle()).isEqualTo("Example");
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FILE);
                assertThat(part.getTitle()).isEqualTo("answer.txt");
                assertThat(part.getMediaType()).isEqualTo("text/plain");
                assertThat(part.getData()).isEqualTo("SGVsbG8=");
            })
            .verifyComplete();
    }

    @Test
    void streamText_exposesPartialOutputStreamForObject() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("{\"name\":\"Halo\"}", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Generate project")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.partialOutputStream())
            .expectNext(Map.of("name", "Halo"))
            .verifyComplete();
        StepVerifier.create(result.output())
            .expectNext(Map.of("name", "Halo"))
            .verifyComplete();
    }

    @Test
    void streamText_exposesValidatedElementStreamForArray() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("[", null, null, null),
            chatResponse("{\"name\":\"Halo\"},", null, null, null),
            chatResponse("{\"name\":\"CMS\"}]", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Generate projects")
            .output(OutputSpec.array(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.elementStream())
            .expectNext(Map.of("name", "Halo"))
            .expectNext(Map.of("name", "CMS"))
            .verifyComplete();
        StepVerifier.create(result.output())
            .expectNext(List.of(Map.of("name", "Halo"), Map.of("name", "CMS")))
            .verifyComplete();
    }

    @Test
    void generateText_warnsWhenStructuredOutputFallsBackToPromptGuidance() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("{\"name\":\"Halo\"}", "stop", 2, 4)
        );
        var model = new LanguageModelImpl(chatModel, "generic");
        var request = GenerateTextRequest.builder()
            .prompt("Generate project")
            .output(OutputSpec.builder()
                .type(run.halo.aifoundation.schema.OutputType.OBJECT)
                .schema(Map.of(
                    "type", "object",
                    "properties", Map.of("name", Map.of("type", "string")),
                    "required", List.of("name")
                ))
                .strict(true)
                .build())
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getWarnings()).extracting("code")
                .contains("structured-output-prompt-guidance",
                    "structured-output-strict-not-guaranteed"))
            .verifyComplete();
    }

    @Test
    void generateText_structuredValidationErrorContainsSafeContext() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("{}", "stop", 2, 4)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Generate project")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(StructuredOutputValidationException.class);
                var validationError = (StructuredOutputValidationException) error;
                assertThat(validationError.getOutputType())
                    .isEqualTo(run.halo.aifoundation.schema.OutputType.OBJECT);
                assertThat(validationError.getOutputText()).isEqualTo("{}");
                assertThat(validationError.getValidationPath()).isEqualTo("$.name");
                assertThat(validationError.getStepIndex()).isZero();
                assertThat(validationError.getUsage().getTotalTokens()).isEqualTo(6);
                assertThat(validationError.getResponse()).isNotNull();
            })
            .verify();
    }

    @Test
    void streamText_emitsReasoningPartsSeparately() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            reasoningResponse("Thinking", "", null, null, null, null),
            chatResponse("Answer", null, null, null),
            chatResponse("", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        var stream = model.streamText(GenerateTextRequest.builder().prompt("Hi").build());

        StepVerifier.create(stream.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.REASONING_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.REASONING_DELTA);
                assertThat(part.getDelta()).isEqualTo("Thinking");
            })
            .expectNextMatches(part -> PartType.REASONING_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("Answer");
            })
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        assertNoOverlappingStreamBlocks(stream.fullStream().collectList().block());
    }

    @Test
    void streamProtocolNormalizerClosesOverlappingBlocks() {
        var parts = StreamProtocolNormalizer.normalize(Flux.just(
            TextStreamPart.textStart("txt_1"),
            TextStreamPart.textDelta("txt_1", "Answer"),
            TextStreamPart.reasoningStart("rsn_1"),
            TextStreamPart.reasoningDelta("rsn_1", "Thinking", Map.of()),
            TextStreamPart.finish(FinishReason.STOP, "stop", null)
        )).collectList().block();

        assertThat(parts).extracting(TextStreamPart::getType).containsExactly(
            PartType.TEXT_START,
            PartType.TEXT_DELTA,
            PartType.TEXT_END,
            PartType.REASONING_START,
            PartType.REASONING_DELTA,
            PartType.REASONING_END,
            PartType.FINISH
        );
        assertNoOverlappingStreamBlocks(parts);
    }

    @Test
    void streamText_emitsSanitizedRawDiagnosticPart() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("Hi", null, null, null),
            chatResponse("",
                "stop",
                1,
                2,
                Map.of("rawResponse", Map.of(
                    "apiKey", "secret-value",
                    "nested", Map.of("authorization", "Bearer token", "safe", "ok")
                )))
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build()).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.RAW);
                assertThat(part.getMetadata()).containsKey("rawResponse");
                assertThat(part.getMetadata().toString()).doesNotContain("secret-value");
                assertThat(part.getMetadata().toString()).doesNotContain("Bearer token");
                assertThat(part.getMetadata().toString()).contains("[REDACTED]");
            })
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();
    }

    @Test
    void streamText_convertsUpstreamErrorToErrorPart() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class)))
            .thenReturn(Flux.error(new IllegalStateException("upstream failed")));
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build()).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.ERROR);
                assertThat(part.getErrorText()).isEqualTo("upstream failed");
            })
            .verifyComplete();
    }

    @Test
    void streamText_emitsToolEventsForToolRequests() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(
                chatResponse("Let me check.", null, null, null),
                toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)
            ),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("Let me check.");
            })
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_RESULT.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("It is 22C.");
            })
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        verify(chatModel, times(2)).stream(any(Prompt.class));
    }

    @Test
    void streamText_emitsErrorPartWhenProviderDoesNotSupportTools() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "simple") {
            @Override
            protected boolean supportsToolCalling() {
                return false;
            }
        };
        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .tools(List.of(ToolDefinition.builder().name("weather").build()))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.ERROR);
                assertThat(part.getErrorText())
                    .isEqualTo("Tool calling is not supported by provider type: simple");
            })
            .verifyComplete();
    }

    @Test
    void streamText_emitsToolErrorForFailedToolExecution() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.error(
                    new IllegalStateException("tool failed")))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                assertThat(part.getErrorText()).isEqualTo("tool failed");
            })
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();
    }

    @Test
    void streamText_stopsWhenConditionRejectsNextStepAndEmitsAggregateUsage() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(1))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_RESULT.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FINISH_STEP);
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FINISH);
                assertThat(part.getUsage().getInputTokens()).isEqualTo(2);
                assertThat(part.getUsage().getOutputTokens()).isEqualTo(3);
                assertThat(part.getUsage().getTotalTokens()).isEqualTo(5);
            })
            .verifyComplete();
    }

    @Test
    void generateText_mapsStructuredObjectOutput() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("{\"name\":\"Halo\",\"labels\":[\"cms\"]}", "stop", 2, 4)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Generate project info")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of(
                    "name", Map.of("type", "string"),
                    "labels", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("name")
            )))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getOutput()).isInstanceOf(Map.class);
                assertThat((Map<String, Object>) result.getOutput())
                    .containsEntry("name", "Halo");
                assertThat(result.getOutputText()).isEqualTo("{\"name\":\"Halo\",\"labels\":[\"cms\"]}");
                assertThat(result.getSteps().get(0).getOutput()).isEqualTo(result.getOutput());
                assertThat(result.getContent()).extracting("type").containsExactly(PartType.TEXT);
            })
            .verifyComplete();
    }

    @Test
    void generateText_mapsStructuredArrayAndJsonOutput() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("[{\"name\":\"Halo\"}]", "stop", 2, 4),
            chatResponse("[\"alpha\",\"beta\"]", "stop", 2, 4)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Generate projects")
                .output(OutputSpec.array(Map.of(
                    "type", "object",
                    "properties", Map.of("name", Map.of("type", "string")),
                    "required", List.of("name")
                )))
                .build()))
            .assertNext(result -> {
                assertThat(result.getOutput()).isInstanceOf(List.class);
                assertThat((List<?>) result.getOutput()).hasSize(1);
            })
            .verifyComplete();

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Generate JSON")
                .output(OutputSpec.json())
                .build()))
            .assertNext(result -> assertThat(result.getOutput()).isEqualTo(List.of("alpha", "beta")))
            .verifyComplete();
    }

    @Test
    void generateText_supportsStructuredOutputFromRecordClass() {
        var schema = OutputSpec.object(ProjectInfo.class).getSchema();

        assertThat(schema).containsEntry("type", "object");
        assertThat((Map<String, Object>) schema.get("properties"))
            .containsKeys("name", "labels");
    }

    @Test
    void generateText_validatesChoiceOutput() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("sunny", "stop", 2, 4));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Classify weather")
            .output(OutputSpec.choice(List.of("sunny", "rainy")))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getOutput()).isEqualTo("sunny"))
            .verifyComplete();
    }

    @Test
    void generateText_failsWhenStructuredOutputIsInvalid() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{\"age\":\"old\"}", "stop", 2, 4));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Generate age")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("age", Map.of("type", "integer")),
                "required", List.of("age")
            )))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("Structured output validation failed: $.age must be integer")
            .verify();
    }

    @Test
    void streamText_streamsStructuredOutputAsTextAndValidatesAtFinish() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("{\"name\":\"Halo\"}", null, null, null),
            chatResponse("", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Generate project")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FINISH_STEP);
            })
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();
    }

    @Test
    void streamText_structuredValidationErrorFailsResultAndOutputWithContext() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            chatResponse("{}", null, null, null),
            chatResponse("", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Generate project")
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name")
            )))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.ERROR);
                assertThat(part.getErrorText()).contains("missing required field");
                assertThat(part.getProviderMetadata()).containsEntry("validationPath", "$.name");
            })
            .verifyComplete();

        StepVerifier.create(result.result())
            .expectErrorSatisfies(this::assertStructuredStreamValidationError)
            .verify();
        StepVerifier.create(result.output())
            .expectErrorSatisfies(this::assertStructuredStreamValidationError)
            .verify();
    }

    @Test
    void generateText_parsesStructuredOutputFromFinalToolStepOnly() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("{\"answer\":\"It is 22C\"}", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(Map.of("type", "object"))
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .output(OutputSpec.object(Map.of(
                "type", "object",
                "properties", Map.of("answer", Map.of("type", "string")),
                "required", List.of("answer")
            )))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getOutput()).isEqualTo(Map.of("answer", "It is 22C"));
                assertThat(result.getSteps().get(0).getOutput()).isNull();
                assertThat(result.getSteps().get(1).getOutput()).isEqualTo(result.getOutput());
            })
            .verifyComplete();
    }

    @Test
    void generateText_validatesToolInputAndOutputSchemas() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":12}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("location", Map.of("type", "string")),
                    "required", List.of("location")
                ))
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getSteps().get(0).getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("location")))
            .verifyComplete();
    }

    @Test
    void generateText_recordsToolErrorWhenToolOutputSchemaFails() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("location", Map.of("type", "string")),
                    "required", List.of("location")
                ))
                .outputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("temperature", Map.of("type", "integer")),
                    "required", List.of("temperature")
                ))
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", "hot")))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getSteps().get(0).getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("temperature")))
            .verifyComplete();
    }

    @Test
    void streamText_recordsToolErrorForUnknownTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "unknown", "{}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of()))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                assertThat(part.getErrorText()).contains("Unknown tool");
            })
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_warnsAndStopsWhenToolHasNoExecutor() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.FINISH_STEP);
                assertThat(part.getWarnings()).extracting("code").contains("tool-not-executed");
            })
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_preservesReasoningWhenContinuingAfterToolCall() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(reasoningToolCallResponse("call_1", "weather", "{\"location\":\"SF\"}",
                "Need weather data.", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "deepseek", reasoningToolProviderOptions());

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        var stream = model.streamText(request);

        StepVerifier.create(stream.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.REASONING_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.REASONING_DELTA);
                assertThat(part.getDelta()).isEqualTo("Need weather data.");
            })
            .expectNextMatches(part -> PartType.REASONING_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_RESULT.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        assertNoOverlappingStreamBlocks(stream.fullStream().collectList().block());

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions())
            .filteredOn(message -> "assistant".equals(message.getMessageType().getValue()))
            .singleElement()
            .satisfies(message -> assertThat(message.getMetadata())
                .containsEntry("reasoningContent", "Need weather data."));
    }

    @Test
    void generateText_invokesLifecycleCallbacksForMultiStepToolCall() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var events = new ArrayList<String>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .metadata(Map.of("requestId", "req-1"))
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .lifecycle(new GenerationLifecycle() {
                @Override
                public Mono<Void> onStart(run.halo.aifoundation.lifecycle.GenerationStartEvent event) {
                    events.add("start:" + event.getMetadata().get("requestId"));
                    return Mono.empty();
                }

                @Override
                public Mono<Void> onStepStart(GenerationStepStartEvent event) {
                    events.add("step-start:" + event.getStepIndex());
                    return Mono.empty();
                }

                @Override
                public Mono<Void> onToolCallStart(GenerationToolCallStartEvent event) {
                    events.add("tool-start:" + event.getToolName());
                    return Mono.empty();
                }

                @Override
                public Mono<Void> onToolCallFinish(GenerationToolCallFinishEvent event) {
                    events.add("tool-finish:" + event.getToolName());
                    assertThat(event.getDuration()).isNotNull();
                    return Mono.empty();
                }

                @Override
                public Mono<Void> onStepFinish(GenerationStepFinishEvent event) {
                    events.add("step-finish:" + event.getStepIndex());
                    return Mono.empty();
                }

                @Override
                public Mono<Void> onFinish(run.halo.aifoundation.lifecycle.GenerationFinishEvent event) {
                    events.add("finish:" + event.getResult().getText());
                    return Mono.empty();
                }
            })
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("It is 22C."))
            .verifyComplete();

        assertThat(events).containsExactly(
            "start:req-1",
            "step-start:0",
            "tool-start:weather",
            "tool-finish:weather",
            "step-finish:0",
            "step-start:1",
            "step-finish:1",
            "finish:It is 22C."
        );
    }

    @Test
    void streamText_invokesLifecycleOnceAcrossMultipleProjections() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class)))
            .thenReturn(Flux.just(chatResponse("Done", "stop", 3, 5)));
        var model = new LanguageModelImpl(chatModel, "openai");
        var starts = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .lifecycle(new GenerationLifecycle() {
                @Override
                public Mono<Void> onStart(run.halo.aifoundation.lifecycle.GenerationStartEvent event) {
                    starts.incrementAndGet();
                    return Mono.empty();
                }
            })
            .build();

        var stream = model.streamText(request);

        StepVerifier.create(stream.textStream())
            .expectNext("Done")
            .verifyComplete();
        StepVerifier.create(stream.result())
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();

        assertThat(starts).hasValue(1);
        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void generateText_capturesCallbackFailureAsWarning() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 3, 5));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .lifecycle(new GenerationLifecycle() {
                @Override
                public Mono<Void> onStepStart(GenerationStepStartEvent event) {
                    return Mono.error(new IllegalStateException("callback unavailable"));
                }
            })
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getWarnings())
                .anySatisfy(warning -> {
                    assertThat(warning.getCode()).isEqualTo("lifecycle-callback-failed");
                    assertThat(warning.getMessage()).contains("onStepStart");
                }))
            .verifyComplete();
    }

    @Test
    void generateText_failsBeforeProviderWhenCancelled() {
        var chatModel = mock(ChatModel.class);
        var model = new LanguageModelImpl(chatModel, "openai");
        var source = new CancellationSource();
        source.cancel();

        var request = GenerateTextRequest.builder()
            .prompt("Hello")
            .cancellationToken(source.token())
            .build();

        StepVerifier.create(model.generateText(request))
            .expectError(AiGenerationCancelledException.class)
            .verify();

        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void generateText_recordsToolTimeoutAsToolError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
            .thenReturn(toolCallResponse("call_1", "slow", "{}", 2, 3));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Call slow")
            .tools(List.of(ToolDefinition.builder()
                .name("slow")
                .executor(context -> Mono.never())
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .timeouts(GenerationTimeouts.builder()
                .toolTimeout(Duration.ofMillis(20))
                .build())
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("tool timed out")))
            .verifyComplete();
    }

    private ChatResponse chatResponse(String text, String finishReason, Integer promptTokens,
        Integer completionTokens) {
        return chatResponse(text, finishReason, promptTokens, completionTokens, Map.of());
    }

    private ChatResponse reasoningResponse(String reasoning, String text, String finishReason,
        Integer promptTokens, Integer completionTokens, Integer reasoningTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_reasoning")
            .model("test-model");
        if (promptTokens != null || completionTokens != null || reasoningTokens != null) {
            var nativeUsage = new OpenAiApi.Usage(completionTokens, promptTokens,
                promptTokens != null && completionTokens != null ? promptTokens + completionTokens : null,
                null,
                new OpenAiApi.Usage.CompletionTokenDetails(reasoningTokens, null, null, null));
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens,
                nativeUsage.totalTokens(), nativeUsage));
        }
        var output = AssistantMessage.builder()
            .content(text)
            .properties(Map.of("reasoningContent", reasoning))
            .build();
        return new ChatResponse(
            List.of(new Generation(output, generationMetadata)),
            metadataBuilder.build()
        );
    }

    private ChatResponse chatResponse(String text, String finishReason, Integer promptTokens,
        Integer completionTokens, Map<String, Object> metadata) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_1")
            .model("test-model")
            .metadata(metadata);
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(text), generationMetadata)),
            metadataBuilder.build()
        );
    }

    private ChatResponse toolCallResponse(String id, String name, String arguments,
        Integer promptTokens, Integer completionTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason("tool_calls")
            .build();
        var output = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_tool")
            .model("test-model");
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(
            List.of(new Generation(output, generationMetadata)),
            metadataBuilder.build()
        );
    }

    private ChatResponse reasoningToolCallResponse(String id, String name, String arguments,
        String reasoning, Integer promptTokens, Integer completionTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason("tool_calls")
            .build();
        var output = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", reasoning))
            .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_tool")
            .model("test-model");
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(
            List.of(new Generation(output, generationMetadata)),
            metadataBuilder.build()
        );
    }

    private LanguageModelProviderOptions reasoningToolProviderOptions() {
        return new LanguageModelProviderOptions(true, true, (request, toolCallbacks, toolNames) -> {
            var builder = OpenAiChatOptions.builder()
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxOutputTokens())
                .topP(request.getTopP())
                .presencePenalty(request.getPresencePenalty())
                .frequencyPenalty(request.getFrequencyPenalty())
                .stop(request.getStopSequences())
                .internalToolExecutionEnabled(false)
                .toolCallbacks(toolCallbacks);
            var providerOptions = request.getProviderOptions() != null
                ? request.getProviderOptions().get("deepseek")
                : null;
            if (providerOptions != null && !providerOptions.isEmpty()) {
                builder.extraBody(Map.copyOf(providerOptions));
            }
            if (!toolNames.isEmpty()) {
                builder.toolNames(toolNames);
            }
            return builder.build();
        }, null);
    }

    private void assertStructuredStreamValidationError(Throwable error) {
        assertThat(error).isInstanceOf(StructuredOutputValidationException.class);
        var validationError = (StructuredOutputValidationException) error;
        assertThat(validationError.getOutputType())
            .isEqualTo(run.halo.aifoundation.schema.OutputType.OBJECT);
        assertThat(validationError.getOutputText()).isEqualTo("{}");
        assertThat(validationError.getValidationPath()).isEqualTo("$.name");
        assertThat(validationError.getStepIndex()).isZero();
        assertThat(validationError.getUsage().getTotalTokens()).isEqualTo(6);
        assertThat(validationError.getResponse()).isNotNull();
    }

    private void assertNoOverlappingStreamBlocks(List<TextStreamPart> parts) {
        String activeBlock = null;
        for (var part : parts) {
            switch (part.getType()) {
                case PartType.TEXT_START -> {
                    assertThat(activeBlock)
                        .as("text-start must not open while another stream block is active")
                        .isNull();
                    activeBlock = PartType.TEXT_START;
                }
                case PartType.TEXT_END -> {
                    assertThat(activeBlock)
                        .as("text-end must close an active text block")
                        .isEqualTo(PartType.TEXT_START);
                    activeBlock = null;
                }
                case PartType.REASONING_START -> {
                    assertThat(activeBlock)
                        .as("reasoning-start must not open while another stream block is active")
                        .isNull();
                    activeBlock = PartType.REASONING_START;
                }
                case PartType.REASONING_END -> {
                    assertThat(activeBlock)
                        .as("reasoning-end must close an active reasoning block")
                        .isEqualTo(PartType.REASONING_START);
                    activeBlock = null;
                }
                default -> {
                }
            }
        }
        assertThat(activeBlock).as("stream block must be closed").isNull();
    }

    private record ProjectInfo(String name, List<String> labels) {
    }
}
