package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.aifoundation.FinishReason;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.ModelMessage;
import run.halo.aifoundation.ModelMessagePart;
import run.halo.aifoundation.ModelMessageRole;
import run.halo.aifoundation.TextStreamPart;
import run.halo.aifoundation.ToolDefinition;

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
                        .isEqualTo(run.halo.aifoundation.ModelMessageRole.ASSISTANT));
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
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");
        var request = GenerateTextRequest.builder()
            .messages(List.of(ModelMessage.builder()
                .role(ModelMessageRole.USER)
                .content(List.of(ModelMessagePart.builder().type("image").build()))
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("unsupported content part type: image")
            .verify();
    }

    @Test
    void generateText_executesToolAndContinuesWhenMaxStepsAllows() {
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
                .description("Get weather")
                .inputSchema(Map.of("type", "object"))
                .executor(input -> reactor.core.publisher.Mono.just(Map.of(
                    "location", input.get("location"),
                    "temperature", 22
                )))
                .build()))
            .maxSteps(2)
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
            })
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .contains("tool");
    }

    @Test
    void generateText_deepSeekToolRequestsDisableThinkingMode() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "deepseek");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(input -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .maxSteps(1)
            .build();

        StepVerifier.create(model.generateText(request))
            .expectNextCount(1)
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
        var options = (OpenAiChatOptions) captor.getValue().getOptions();
        assertThat(options.getExtraBody())
            .containsEntry("thinking", Map.of("type", "disabled"));
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
                .executor(input -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getSteps()).hasSize(1);
                assertThat(result.getSteps().get(0).getToolResults()).isEmpty();
                assertThat(result.getWarnings())
                    .extracting("code")
                    .contains("max-steps-reached");
            })
            .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
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
                .executor(input -> reactor.core.publisher.Mono.just(Map.of()))
                .build()))
            .maxSteps(2)
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
            .maxSteps(2)
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
                .executor(input -> reactor.core.publisher.Mono.error(
                    new IllegalStateException("tool failed")))
                .build()))
            .maxSteps(2)
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

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(part -> assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_START))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_START_STEP);
                assertThat(part.getStepIndex()).isZero();
            })
            .assertNext(part -> assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_TEXT_START))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("Hel");
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("lo");
            })
            .assertNext(part -> assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_TEXT_END))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_FINISH_STEP);
                assertThat(part.getStepIndex()).isZero();
                assertThat(part.getFinishReason()).isEqualTo(FinishReason.STOP);
                assertThat(part.getUsage().getInputTokens()).isEqualTo(2);
                assertThat(part.getUsage().getOutputTokens()).isEqualTo(4);
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_FINISH);
                assertThat(part.getFinishReason()).isEqualTo(FinishReason.STOP);
                assertThat(part.getUsage().getInputTokens()).isEqualTo(2);
                assertThat(part.getUsage().getOutputTokens()).isEqualTo(4);
            })
            .verifyComplete();
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

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TEXT_DELTA.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_RAW);
                assertThat(part.getMetadata()).containsKey("rawResponse");
                assertThat(part.getMetadata().toString()).doesNotContain("secret-value");
                assertThat(part.getMetadata().toString()).doesNotContain("Bearer token");
                assertThat(part.getMetadata().toString()).contains("[REDACTED]");
            })
            .expectNextMatches(part -> TextStreamPart.TYPE_TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH.equals(part.getType()))
            .verifyComplete();
    }

    @Test
    void streamText_convertsUpstreamErrorToErrorPart() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class)))
            .thenReturn(Flux.error(new IllegalStateException("upstream failed")));
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TEXT_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_ERROR);
                assertThat(part.getErrorText()).isEqualTo("upstream failed");
            })
            .verifyComplete();
    }

    @Test
    void streamText_emitsToolEventsForToolRequests() {
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
                .executor(input -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .maxSteps(2)
            .build();

        StepVerifier.create(model.streamText(request))
            .expectNextMatches(part -> TextStreamPart.TYPE_START.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TOOL_CALL.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TOOL_RESULT.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TEXT_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("It is 22C.");
            })
            .expectNextMatches(part -> TextStreamPart.TYPE_TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH.equals(part.getType()))
            .verifyComplete();
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

        StepVerifier.create(model.streamText(request))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_ERROR);
                assertThat(part.getErrorText())
                    .isEqualTo("Tool calling is not supported by provider type: simple");
            })
            .verifyComplete();
    }

    @Test
    void streamText_emitsToolErrorForFailedToolExecution() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(input -> reactor.core.publisher.Mono.error(
                    new IllegalStateException("tool failed")))
                .build()))
            .maxSteps(2)
            .build();

        StepVerifier.create(model.streamText(request))
            .expectNextMatches(part -> TextStreamPart.TYPE_START.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TOOL_CALL.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_TOOL_ERROR);
                assertThat(part.getErrorText()).isEqualTo("tool failed");
            })
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_FINISH.equals(part.getType()))
            .verifyComplete();
    }

    @Test
    void streamText_stopsAtMaxStepsAndEmitsAggregateUsage() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(input -> reactor.core.publisher.Mono.just(Map.of("temperature", 22)))
                .build()))
            .maxSteps(1)
            .build();

        StepVerifier.create(model.streamText(request))
            .expectNextMatches(part -> TextStreamPart.TYPE_START.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_START_STEP.equals(part.getType()))
            .expectNextMatches(part -> TextStreamPart.TYPE_TOOL_CALL.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_FINISH_STEP);
                assertThat(part.getWarnings()).extracting("code").contains("max-steps-reached");
            })
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(TextStreamPart.TYPE_FINISH);
                assertThat(part.getUsage().getInputTokens()).isEqualTo(2);
                assertThat(part.getUsage().getOutputTokens()).isEqualTo(3);
                assertThat(part.getUsage().getTotalTokens()).isEqualTo(5);
            })
            .verifyComplete();
    }

    private ChatResponse chatResponse(String text, String finishReason, Integer promptTokens,
        Integer completionTokens) {
        return chatResponse(text, finishReason, promptTokens, completionTokens, Map.of());
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
}
