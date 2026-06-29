package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.lifecycle.GenerationStepFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationStepStartEvent;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.lifecycle.GenerationToolCallFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationToolCallStartEvent;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
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
import run.halo.aifoundation.provider.DeepSeekProvider;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolExecutor;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ReasoningControlOptions;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
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
                assertThat(result.getResponseMessages())
                    .singleElement()
                    .satisfies(message -> {
                        assertThat(message.getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
                        assertThat(message.getContent().getFirst().getType())
                            .isEqualTo(PartType.TEXT);
                        assertThat(message.getContent().getFirst().getText()).isEqualTo("Done");
                    });
                assertThat(result.getSteps())
                    .singleElement()
                    .satisfies(step -> {
                        assertThat(step.getStepIndex()).isZero();
                        assertThat(step.getText()).isEqualTo("Done");
                        assertThat(step.getUsage().getTotalTokens()).isEqualTo(8);
                        assertThat(step.getResponseMessages())
                            .containsExactlyElementsOf(result.getResponseMessages());
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
    void generateText_mapsUserImageDataToSpringMedia() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 3, 5));
        var model = languageModelWithCapabilities(chatModel, LanguageCapability.builder()
            .imageInput(true)
            .inputMediaTypes(List.of("image/*"))
            .inputSources(List.of(InputSource.DATA))
            .build());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
                    ModelMessagePart.text("Describe this"),
                    ModelMessagePart.image(DataContent.data(new byte[] {1, 2, 3}, "image/png",
                        "diagram.png"))
                ))))
                .build()))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        var userMessage = (UserMessage) captor.getValue().getInstructions().getFirst();
        assertThat(userMessage.getText()).isEqualTo("Describe this");
        assertThat(userMessage.getMedia())
            .singleElement()
            .satisfies(media -> {
                assertThat(media.getMimeType().toString()).isEqualTo("image/png");
                assertThat(media.getName()).isEqualTo("diagram.png");
                assertThat((byte[]) media.getData()).containsExactly(1, 2, 3);
            });
        assertThat(model.capabilities().modelCapabilities().getLanguage().getImageInput()).isTrue();
    }

    @Test
    void generateText_mapsUserFileDataToSpringMedia() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 3, 5));
        var model = languageModelWithCapabilities(chatModel, LanguageCapability.builder()
            .fileInput(true)
            .inputMediaTypes(List.of("application/pdf"))
            .inputSources(List.of(InputSource.DATA))
            .build());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
                    ModelMessagePart.text("Summarize this"),
                    ModelMessagePart.file(DataContent.data(new byte[] {4, 5}, "application/pdf",
                        "brief.pdf"))
                ))))
                .build()))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        var userMessage = (UserMessage) captor.getValue().getInstructions().getFirst();
        assertThat(userMessage.getMedia())
            .singleElement()
            .satisfies(media -> {
                assertThat(media.getMimeType().toString()).isEqualTo("application/pdf");
                assertThat(media.getName()).isEqualTo("brief.pdf");
                assertThat((byte[]) media.getData()).containsExactly(4, 5);
            });
    }

    @Test
    void generateText_preservesAssistantUrlMediaHistory() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 3, 5));
        var model = languageModelWithCapabilities(chatModel, LanguageCapability.builder()
            .imageInput(true)
            .inputMediaTypes(List.of("image/*"))
            .inputSources(List.of(InputSource.URL))
            .build());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(
                    ModelMessage.assistant(List.of(
                        ModelMessagePart.text("Generated this image earlier."),
                        ModelMessagePart.image(DataContent.url("https://example.com/image.png",
                            "image/png", "image.png"))
                    )),
                    ModelMessage.user("Use that image as context.")
                ))
                .build()))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        var assistantMessage = captor.getValue().getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .findFirst()
            .orElseThrow();
        assertThat(assistantMessage.getText()).isEqualTo("Generated this image earlier.");
        assertThat(assistantMessage.getMedia())
            .singleElement()
            .extracting(Media::getData)
            .isEqualTo("https://example.com/image.png");
    }

    @Test
    void generateText_rejectsSystemMediaBeforeProviderCall() {
        var chatModel = mock(ChatModel.class);
        var model = languageModelWithCapabilities(chatModel, LanguageCapability.builder()
            .imageInput(true)
            .inputMediaTypes(List.of("image/*"))
            .inputSources(List.of(InputSource.DATA))
            .build());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(new ModelMessage(ModelMessageRole.SYSTEM, List.of(
                    ModelMessagePart.image(DataContent.data(new byte[] {1}, "image/png"))
                ))))
                .build()))
            .expectErrorMessage("media content part is only supported for user or assistant messages")
            .verify();

        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void generateText_rejectsSourcePartBeforeProviderCall() {
        var chatModel = mock(ChatModel.class);
        var model = new LanguageModelImpl(chatModel, "openai");
        var sourcePart = new ModelMessagePart();
        sourcePart.setType(PartType.SOURCE);

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(sourcePart))))
                .build()))
            .expectErrorMessage("unsupported content part type: source")
            .verify();

        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void generateText_rejectsUnknownImageCapabilityBeforeProviderCall() {
        var chatModel = mock(ChatModel.class);
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
                    ModelMessagePart.image(DataContent.data(new byte[] {1}, "image/png"))
                ))))
                .build()))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(UnsupportedModelCapabilityException.class);
                var capabilityError = (UnsupportedModelCapabilityException) error;
                assertThat(capabilityError.getCapabilityPath()).isEqualTo("language.imageInput");
                assertThat(capabilityError.getExpectedValue()).isEqualTo(Boolean.TRUE);
                assertThat(capabilityError.getActualValue()).isNull();
                assertThat(capabilityError.getMessageIndex()).isZero();
                assertThat(capabilityError.getPartIndex()).isZero();
            })
            .verify();

        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void generateText_rejectsUnsupportedUrlInputSourceBeforeProviderCall() {
        var chatModel = mock(ChatModel.class);
        var model = languageModelWithCapabilities(chatModel, LanguageCapability.builder()
            .imageInput(true)
            .inputMediaTypes(List.of("image/*"))
            .inputSources(List.of(InputSource.DATA))
            .build());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .messages(List.of(new ModelMessage(ModelMessageRole.USER, List.of(
                    ModelMessagePart.image(DataContent.url("https://example.com/image.png",
                        "image/png"))
                ))))
                .build()))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(UnsupportedModelCapabilityException.class);
                var capabilityError = (UnsupportedModelCapabilityException) error;
                assertThat(capabilityError.getCapabilityPath()).isEqualTo("language.inputSources");
                assertThat(capabilityError.getExpectedValue()).isEqualTo(List.of(InputSource.URL));
                assertThat(capabilityError.getActualValue()).isEqualTo(List.of(InputSource.DATA));
            })
            .verify();

        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void generateText_retriesRetryableProviderFailures() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new IllegalStateException("temporary outage"))
            .thenReturn(chatResponse("Recovered", "stop", 1, 1));
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .maxRetries(1)
                .build()))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Recovered"))
            .verifyComplete();

        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void generateText_disablesRetriesWhenMaxRetriesIsZero() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new IllegalStateException("temporary outage"));
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .maxRetries(0)
                .build()))
            .expectErrorMessage("temporary outage")
            .verify();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateText_rejectsNegativeMaxRetries() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .maxRetries(-1)
                .build()))
            .expectErrorMessage("maxRetries must not be negative")
            .verify();
    }

    @Test
    void generateText_rejectsSeedWhenProviderCannotMapIt() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "simple");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .seed(42)
                .build()))
            .expectErrorMessage("seed is not supported by provider type: simple")
            .verify();
    }

    @Test
    void generateText_rejectsSeedWhenDeepSeekDedicatedProviderCannotMapIt() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "deepseek",
            new DeepSeekProvider().languageModelProviderOptions());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .seed(42)
                .build()))
            .expectErrorMessage("seed is not supported by provider type: deepseek")
            .verify();
    }

    @Test
    void generateText_rejectsHeadersWhenDeepSeekDedicatedProviderCannotMapThem() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "deepseek",
            new DeepSeekProvider().languageModelProviderOptions());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .headers(Map.of("X-Trace-Id", "trace-1"))
                .build()))
            .expectErrorMessage("Request headers are not supported by provider type: deepseek")
            .verify();
    }

    @Test
    void generateText_prepareStepCanOverrideSeedAndRetryBudget() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 1, 1));
        var providerOptions = LanguageModelProviderOptions.builder()
            .requestHeadersSupported(true)
            .seedSupported(true)
            .chatOptionsFactory(request -> OpenAiCompatibleChatOptions.builder()
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxOutputTokens())
                .seed(request.getSeed())
                .build())
            .build();
        var model = new LanguageModelImpl(chatModel, "openai", providerOptions);

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Hello")
                .seed(1)
                .maxRetries(2)
                .prepareStep(context -> {
                    assertThat(context.getSeed()).isEqualTo(1);
                    assertThat(context.getMaxRetries()).isEqualTo(2);
                    return PreparedStep.builder()
                        .seed(42)
                        .maxRetries(0)
                        .build();
                })
                .build()))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Done"))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(((OpenAiCompatibleChatOptions) captor.getValue().getOptions()).getSeed()).isEqualTo(42);
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
        assertThatThrownBy(() -> ModelMessagePart.builder().type("unsupported").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("unsupported message part type: unsupported");
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
                        assertThat(reasoning.getProviderMetadata()).isEmpty();
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
    void generateText_extractsDeepSeekAssistantMessageReasoningContent() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            deepSeekReasoningResponse("Think through DeepSeek.", "Answer.", "stop")
        );
        var model = new LanguageModelImpl(chatModel, "deepseek",
            new DeepSeekProvider().languageModelProviderOptions());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Answer.");
                assertThat(result.getReasoningText()).isEqualTo("Think through DeepSeek.");
                assertThat(result.getContent()).extracting("type")
                    .containsExactly(PartType.REASONING, PartType.TEXT);
            })
            .verifyComplete();
    }

    @Test
    void generateText_omitsReasoningFromResponseMessagesWhenProviderUnsupported() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            reasoningToolCallResponse("call_1", "weather", "{\"location\":\"SF\"}",
                "Think first.", 3, 5)
        );
        var model = new LanguageModelImpl(chatModel, "mimo");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getReasoningText()).isEqualTo("Think first.");
                assertThat(result.getContent()).extracting("type")
                    .containsExactly(PartType.REASONING, PartType.TOOL_CALL);
                assertThat(result.getResponseMessages())
                    .singleElement()
                    .satisfies(message -> assertThat(message.getContent())
                        .extracting(ModelMessagePart::getType)
                        .containsExactly(PartType.TOOL_CALL));
            })
            .verifyComplete();
    }

    @Test
    void generateText_extractsTaggedReasoningFromNonStreamingText() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("<think>Think first.</think>Answer.", "stop", 3, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Answer.");
                assertThat(result.getReasoningText()).isEqualTo("Think first.");
                assertThat(result.getReasoning()).singleElement()
                    .satisfies(reasoning -> assertThat(reasoning.getText())
                        .isEqualTo("Think first."));
                assertThat(result.getContent()).extracting("type")
                    .containsExactly(PartType.REASONING, PartType.TEXT);
            })
            .verifyComplete();
    }

    @Test
    void generateText_keepsUnbalancedReasoningTagAsAnswerText() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("<think>still open Answer.", "stop", 3, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("<think>still open Answer.");
                assertThat(result.getReasoningText()).isNull();
                assertThat(result.getReasoning()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void generateText_prefersMetadataReasoningAndStripsTaggedText() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            assistantResponse("<think>tagged reasoning</think>Answer.",
                Map.of("reasoningContent", "metadata reasoning"), "stop", 3, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Answer.");
                assertThat(result.getReasoningText()).isEqualTo("metadata reasoning");
            })
            .verifyComplete();
    }

    @Test
    void generateText_rejectsUnsupportedExplicitReasoning() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "ollama");
        var request = GenerateTextRequest.builder()
            .prompt("Fast")
            .reasoning(ReasoningOptions.disabled())
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("disabled reasoning is not supported by provider type: ollama")
            .verify();
    }

    @Test
    void generateText_rejectsReasoningProviderOptionConflict() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "thinking-provider",
            LanguageModelProviderOptions.builder()
                .reasoningHistorySupported(true)
                .streamToolCallsForReasoning(true)
                .requestHeadersSupported(true)
                .seedSupported(true)
                .reasoningControlOptions(ReasoningControlOptions.thinkingType((builder, request) -> {
                }))
                .build());
        var request = GenerateTextRequest.builder()
            .prompt("Fast")
            .reasoning(ReasoningOptions.disabled())
            .providerOptions(Map.of("thinking-provider", Map.of(
                "thinking", Map.of("type", "enabled")
            )))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("reasoning setting conflicts with "
                + "providerOptions.thinking-provider.thinking; use either typed reasoning or raw "
                + "provider options, not both")
            .verify();
    }

    @Test
    void generateText_rejectsDisabledReasoningWithReasoningHistory() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "thinking-provider",
            LanguageModelProviderOptions.builder()
                .reasoningHistorySupported(true)
                .streamToolCallsForReasoning(true)
                .requestHeadersSupported(true)
                .seedSupported(true)
                .reasoningControlOptions(ReasoningControlOptions.thinkingType((builder, request) -> {
                }))
                .build());
        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.assistant(List.of(ModelMessagePart.reasoning("thinking"))),
                ModelMessage.user("Continue")
            ))
            .reasoning(ReasoningOptions.disabled())
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("disabled reasoning cannot be combined with assistant reasoning "
                + "history for provider type: thinking-provider")
            .verify();
    }

    @Test
    void generateText_warnsWhenReasoningReturnsAfterDisabledRequest() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            reasoningResponse("Still thinking.", "Answer.", "stop", 3, 5, 2)
        );
        var model = new LanguageModelImpl(chatModel, "thinking-provider",
            LanguageModelProviderOptions.builder()
                .reasoningHistorySupported(true)
                .streamToolCallsForReasoning(true)
                .requestHeadersSupported(true)
                .seedSupported(true)
                .reasoningControlOptions(ReasoningControlOptions.thinkingType((builder, request) -> {
                }))
                .build());

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Fast")
                .reasoning(ReasoningOptions.disabled())
                .build()))
            .assertNext(result -> {
                assertThat(result.getReasoningText()).isEqualTo("Still thinking.");
                assertThat(result.getWarnings()).extracting("code")
                    .contains("reasoning-returned-while-disabled");
            })
            .verifyComplete();
    }

    @Test
    void generateText_missingOrFailedRepairPreservesValidationToolError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3),
            toolCallResponse("call_2", "weather", "{\"city\":\"NYC\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var requestWithoutRepair = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of()))))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        StepVerifier.create(model.generateText(requestWithoutRepair))
            .assertNext(result -> {
                assertThat(result.getToolErrors())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getErrorText()).contains("location"));
                assertThat(result.getWarnings()).extracting("code")
                    .doesNotContain("tool-call-repair-failed");
            })
            .verifyComplete();

        var requestWithFailedRepair = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of()))))
            .toolCallRepair(context -> Mono.just(ToolCallRepairResult.unrepaired()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        StepVerifier.create(model.generateText(requestWithFailedRepair))
            .assertNext(result -> {
                assertThat(result.getToolErrors())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getErrorText()).contains("location"));
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repair-failed");
            })
            .verifyComplete();
    }

    @Test
    void generateText_doesNotRepairUnknownToolOrOutputSchemaFailure() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "unknown", "{}", 2, 3),
            toolCallResponse("call_2", "weather", "{\"location\":\"SF\"}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var repairCalls = new AtomicInteger();

        var unknownRequest = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of()))))
            .toolCallRepair(context -> {
                repairCalls.incrementAndGet();
                return Mono.just(ToolCallRepairResult.unrepaired());
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        StepVerifier.create(model.generateText(unknownRequest))
            .assertNext(result -> assertThat(result.getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("Unknown tool")))
            .verifyComplete();

        var outputFailureRequest = GenerateTextRequest.builder()
            .prompt("Use tool")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .outputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of("temperature", Map.of("type", "integer")),
                    "required", List.of("temperature")
                ))
                .executor(context -> Mono.just(Map.of("temperature", "hot")))
                .build()))
            .toolCallRepair(context -> {
                repairCalls.incrementAndGet();
                return Mono.just(ToolCallRepairResult.unrepaired());
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        StepVerifier.create(model.generateText(outputFailureRequest))
            .assertNext(result -> assertThat(result.getToolErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).contains("temperature")))
            .verifyComplete();

        assertThat(repairCalls).hasValue(0);
    }

    @Test
    void generateText_turnsDeniedApprovalIntoToolErrorWithoutExecuting() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("I will not remove it.",
            "stop", 4, 5));
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .messages(approvalMessages(false, "Too destructive"))
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(true)
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolResults()).isEmpty();
                assertThat(result.getToolErrors())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getErrorText())
                        .isEqualTo("Tool execution denied: Too destructive"));
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.TOOL, ModelMessageRole.ASSISTANT);
                assertThat(result.getResponseMessages().get(0).getContent().getFirst())
                    .satisfies(part -> {
                        assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                        assertThat(part.getErrorText())
                            .isEqualTo("Tool execution denied: Too destructive");
                    });
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateText_doesNotRequestApprovalWhenToolInputIsInvalid() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":12}", 2, 3)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(Map.of("type", "object", "properties",
                    Map.of("location", Map.of("type", "string")), "required",
                    List.of("location")))
                .needsApproval(true)
                .executor(context -> Mono.just(Map.of()))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getToolApprovalRequests()).isEmpty();
                assertThat(result.getToolErrors()).isNotEmpty();
            })
            .verifyComplete();
    }

    @Test
    void generateText_rejectsUnknownApprovalResponseBeforeProviderCall() {
        var chatModel = mock(ChatModel.class);
        var model = new LanguageModelImpl(chatModel, "openai");
        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.user("Run"),
                ModelMessage.tool(List.of(ModelMessagePart.toolApprovalResponse(
                    ToolApprovalResponse.builder()
                        .approvalId("missing")
                        .approved(true)
                        .build())))
            ))
            .tools(List.of(ToolDefinition.builder().name("run").build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("tool approval response references unknown approval: missing")
            .verify();

        verify(chatModel, times(0)).call(any(Prompt.class));
    }

    @Test
    void generateText_doesNotReplayConsumedApprovalResponse() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Already done", "stop", 4, 5));
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();
        var messages = new ArrayList<>(approvalMessages(true, "done"));
        messages.add(ModelMessage.tool(List.of(ModelMessagePart.toolResult(
            run.halo.aifoundation.tool.ToolResult.builder()
                .toolCallId("call_1")
                .toolName("run")
                .result(Map.of("ok", true))
                .build()))));

        var request = GenerateTextRequest.builder()
            .messages(messages)
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getResponseMessages())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
                    assertThat(message.getContent().getFirst().getText()).isEqualTo("Already done");
                }))
            .verifyComplete();

        assertThat(executions).hasValue(0);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateText_doesNotReplayApprovalResponseCompletedByAssistantContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Next answer", "stop", 4, 5));
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(chatResponse("Next answer", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "deepseek",
            new DeepSeekProvider().languageModelProviderOptions());
        var executions = new AtomicInteger();
        var messages = new ArrayList<>(approvalMessages(true, "done"));
        messages.add(ModelMessage.assistant("Tool was already executed."));
        messages.add(ModelMessage.user("Continue with another question"));

        var request = GenerateTextRequest.builder()
            .messages(messages)
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("Next answer"))
            .verifyComplete();

        assertThat(executions).hasValue(0);
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        assertThat(captor.getValue().getInstructions())
            .extracting(message -> message.getMessageType().getValue())
            .containsExactly("user", "assistant", "user");
        assertThat(captor.getValue().getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .flatMap(message -> message.getToolCalls().stream()))
            .isEmpty();
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
            .satisfies(message -> {
                assertThat(message).isInstanceOf(DeepSeekAssistantMessage.class);
                var deepSeekMessage = (DeepSeekAssistantMessage) message;
                assertThat(deepSeekMessage.getReasoningContent()).isEqualTo("Need weather data.");
                assertThat(deepSeekMessage.getMetadata())
                    .containsEntry("reasoningContent", "Need weather data.");
            });
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
        assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiCompatibleChatOptions.class);
        var options = (OpenAiCompatibleChatOptions) captor.getValue().getOptions();
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
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT, ModelMessageRole.TOOL,
                        ModelMessageRole.ASSISTANT);
                assertThat(result.getResponseMessages().get(0).getContent())
                    .extracting(ModelMessagePart::getType)
                    .containsExactly(PartType.TOOL_CALL);
                assertThat(result.getResponseMessages().get(1).getContent().getFirst().getType())
                    .isEqualTo(PartType.TOOL_RESULT);
                assertThat(result.getResponseMessages().get(2).getContent().getFirst().getText())
                    .isEqualTo("It is 22C.");
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
    void generateText_continuesFromExternalToolError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("I could not get the weather.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.user("Weather in SF?"),
                externalToolCallMessage("call_1", "weather", Map.of("location", "SF")),
                ModelMessage.tool(List.of(ModelMessagePart.toolError(ToolError.builder()
                    .toolCallId("call_1")
                    .toolName("weather")
                    .errorText("weather service unavailable")
                    .build())))
            ))
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("I could not get the weather.");
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT);
            })
            .verifyComplete();

        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void generateText_rejectsExternalToolResultWithoutPriorToolCall() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");

        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.user("Weather in SF?"),
                ModelMessage.tool(List.of(ModelMessagePart.toolResult(ToolResult.builder()
                    .toolCallId("call_1")
                    .toolName("weather")
                    .result(Map.of("temperature", 22))
                    .build())))
            ))
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage("tool response references unknown tool call: call_1")
            .verify();
    }

    @Test
    void generateText_rejectsExternalToolErrorWithMismatchedToolName() {
        var model = new LanguageModelImpl(mock(ChatModel.class), "openai");

        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.user("Weather in SF?"),
                externalToolCallMessage("call_1", "weather", Map.of("location", "SF")),
                ModelMessage.tool(List.of(ModelMessagePart.toolError(ToolError.builder()
                    .toolCallId("call_1")
                    .toolName("search")
                    .errorText("failed")
                    .build())))
            ))
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .expectErrorMessage(
                "tool response toolName mismatch for tool call call_1: expected weather but got search")
            .verify();
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
            .assertNext(result -> {
                assertThat(result.getSteps().get(0).getToolErrors())
                    .singleElement()
                    .satisfies(error -> assertThat(error.getErrorText()).isEqualTo("tool failed"));
                assertThat(result.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT, ModelMessageRole.TOOL);
                assertThat(result.getResponseMessages().get(1).getContent().getFirst())
                    .satisfies(part -> {
                        assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                        assertThat(part.getErrorText()).isEqualTo("tool failed");
                    });
            })
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
    void streamText_ignoresNullAssistantMetadataValues() {
        var chatModel = mock(ChatModel.class);
        var properties = new LinkedHashMap<String, Object>();
        properties.put("id", "resp_1");
        properties.put("finishReason", null);
        properties.put("reasoningContent", "thinking");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            assistantResponse("", properties, null, null, null),
            chatResponse("Done", "stop", 2, 4)
        ));
        var model = new LanguageModelImpl(chatModel, "mimo");

        StepVerifier.create(model.streamText(GenerateTextRequest.builder().prompt("Hi").build())
                .fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.REASONING_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.REASONING_DELTA);
                assertThat(part.getDelta()).isEqualTo("thinking");
            })
            .expectNextMatches(part -> PartType.REASONING_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TEXT_DELTA);
                assertThat(part.getDelta()).isEqualTo("Done");
            })
            .thenConsumeWhile(part -> !PartType.FINISH.equals(part.getType()))
            .assertNext(part -> assertThat(part.getFinishReason()).isEqualTo(FinishReason.STOP))
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
    void generateText_warnsWhenStrictToolSchemaIsDowngraded() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Done", "stop", 2, 4));
        var model = new LanguageModelImpl(chatModel, "openai");
        var request = GenerateTextRequest.builder()
            .prompt("Use weather")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .inputSchema(weatherInputSchema())
                .strict(true)
                .build()))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> assertThat(result.getWarnings()).extracting("code")
                .contains("tool-strict-schema-downgraded"))
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
    void streamText_warnsWhenReasoningReturnsAfterDisabledRequest() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            reasoningResponse("Thinking", "Answer", "stop", 2, 4, 1)
        ));
        var model = new LanguageModelImpl(chatModel, "thinking-provider",
            LanguageModelProviderOptions.builder()
                .reasoningHistorySupported(true)
                .streamToolCallsForReasoning(true)
                .requestHeadersSupported(true)
                .seedSupported(true)
                .reasoningControlOptions(ReasoningControlOptions.thinkingType((builder, request) -> {
                }))
                .build());

        var parts = model.streamText(GenerateTextRequest.builder()
                .prompt("Fast")
                .reasoning(ReasoningOptions.disabled())
                .build())
            .fullStream()
            .collectList()
            .block();

        assertThat(parts).filteredOn(part -> PartType.FINISH_STEP.equals(part.getType()))
            .singleElement()
            .satisfies(part -> assertThat(part.getWarnings()).extracting("code")
                .contains("reasoning-returned-while-disabled"));
    }

    @Test
    void streamText_extractsDeepSeekAssistantMessageReasoningContent() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
            deepSeekReasoningResponse("Stream thinking.", "Answer", "stop")
        ));
        var model = new LanguageModelImpl(chatModel, "deepseek",
            new DeepSeekProvider().languageModelProviderOptions());

        var parts = model.streamText(GenerateTextRequest.builder().prompt("Hi").build())
            .fullStream()
            .collectList()
            .block();

        assertThat(parts).isNotNull();
        assertThat(parts).extracting(TextStreamPart::getType)
            .contains(PartType.REASONING_DELTA, PartType.TEXT_DELTA);
        assertThat(parts.stream()
            .filter(part -> PartType.REASONING_DELTA.equals(part.getType()))
            .map(TextStreamPart::getDelta)
            .collect(Collectors.joining())).isEqualTo("Stream thinking.");
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
    void streamText_emitsApprovalRequestAndDoesNotExecuteTool() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "run", "{\"command\":\"rm file\"}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Remove file")
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .needsApproval(true)
                .executor(context -> {
                    executions.incrementAndGet();
                    return Mono.just(Map.of("ok", true));
                })
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        var result = model.streamText(request);

        StepVerifier.create(result.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_CALL.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TOOL_APPROVAL_REQUEST);
                assertThat(part.getApprovalId()).isEqualTo("approval_call_1");
            })
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getToolApprovalRequests()).hasSize(1);
                assertThat(finalResult.getToolResults()).isEmpty();
                assertThat(finalResult.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT, ModelMessageRole.ASSISTANT);
                assertThat(finalResult.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .map(ModelMessagePart::getType))
                    .containsExactly(PartType.TOOL_CALL, PartType.TOOL_APPROVAL_REQUEST);
            })
            .verifyComplete();
        StepVerifier.create(result.textStream()).verifyComplete();
        assertThat(executions).hasValue(0);
    }

    @Test
    void streamText_stopsMixedExecutableAndApprovalToolCallsWithoutUnresolvedCalls() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "weather",
                    "{\"location\":\"SF\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "run",
                    "{\"command\":\"rm file\"}")
            ), 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(
                ToolDefinition.builder()
                    .name("weather")
                    .executor(context -> {
                        executions.incrementAndGet();
                        return Mono.just(Map.of("temperature", 22));
                    })
                    .build(),
                ToolDefinition.builder()
                    .name("run")
                    .needsApproval(true)
                    .executor(context -> {
                        executions.incrementAndGet();
                        return Mono.just(Map.of("ok", true));
                    })
                    .build()
            ))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        var result = model.streamText(request);

        StepVerifier.create(result.fullStream().collectList())
            .assertNext(parts -> assertThat(parts).extracting(TextStreamPart::getType)
                .containsExactly(
                    PartType.START,
                    PartType.START_STEP,
                    PartType.TOOL_CALL,
                    PartType.TOOL_APPROVAL_REQUEST,
                    PartType.FINISH_STEP,
                    PartType.FINISH
                ))
            .verifyComplete();

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getToolCalls())
                    .singleElement()
                    .satisfies(call -> {
                        assertThat(call.getToolCallId()).isEqualTo("call_2");
                        assertThat(call.getToolName()).isEqualTo("run");
                    });
                assertThat(finalResult.getToolApprovalRequests()).hasSize(1);
                assertThat(finalResult.getToolResults()).isEmpty();
                assertThat(finalResult.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .map(ModelMessagePart::getType))
                    .containsExactly(PartType.TOOL_CALL, PartType.TOOL_APPROVAL_REQUEST);
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_executesApprovedToolBeforeStreamingContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(chatResponse("Done", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .messages(approvalMessages(true, "confirmed"))
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .executor(context -> Mono.just(Map.of("ok", true)))
                .build()))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TOOL_RESULT.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.TOOL, ModelMessageRole.ASSISTANT);
                assertThat(finalResult.getResponseMessages().get(0).getContent().getFirst().getType())
                    .isEqualTo(PartType.TOOL_RESULT);
                assertThat(finalResult.getResponseMessages().get(1).getContent().getFirst().getText())
                    .isEqualTo("Done");
            })
            .verifyComplete();
    }

    @Test
    void streamText_turnsDeniedApprovalIntoToolErrorBeforeContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(chatResponse("Denied", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .messages(approvalMessages(false, "No"))
            .tools(List.of(ToolDefinition.builder()
                .name("run")
                .executor(context -> Mono.just(Map.of("ok", true)))
                .build()))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .assertNext(part -> {
                assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                assertThat(part.getErrorText()).isEqualTo("Tool execution denied: No");
            })
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH.equals(part.getType()))
            .verifyComplete();

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.TOOL, ModelMessageRole.ASSISTANT);
                assertThat(finalResult.getResponseMessages().get(0).getContent().getFirst())
                    .satisfies(part -> {
                        assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                        assertThat(part.getErrorText()).isEqualTo("Tool execution denied: No");
                    });
                assertThat(finalResult.getResponseMessages().get(1).getContent().getFirst().getText())
                    .isEqualTo("Denied");
            })
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

        var result = model.streamText(request);

        StepVerifier.create(result.fullStream())
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

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT, ModelMessageRole.TOOL);
                assertThat(finalResult.getResponseMessages().get(1).getContent().getFirst())
                    .satisfies(part -> {
                        assertThat(part.getType()).isEqualTo(PartType.TOOL_ERROR);
                        assertThat(part.getErrorText()).isEqualTo("tool failed");
                    });
            })
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
            chatResponse("<think>Use JSON.</think>{\"name\":\"Halo\",\"labels\":[\"cms\"]}",
                "stop", 2, 4)
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
                assertThat(result.getReasoningText()).isEqualTo("Use JSON.");
                assertThat(result.getSteps().get(0).getOutput()).isEqualTo(result.getOutput());
                assertThat(result.getContent()).extracting("type")
                    .containsExactly(PartType.REASONING, PartType.TEXT);
            })
            .verifyComplete();
    }

    @Test
    void generateText_mapsStructuredArrayAndJsonOutput() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("<think>Return array.</think>[{\"name\":\"Halo\"}]", "stop", 2, 4),
            chatResponse("<reasoning>Return json.</reasoning>[\"alpha\",\"beta\"]", "stop", 2, 4)
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
                assertThat(result.getReasoningText()).isEqualTo("Return array.");
            })
            .verifyComplete();

        StepVerifier.create(model.generateText(GenerateTextRequest.builder()
                .prompt("Generate JSON")
                .output(OutputSpec.json())
                .build()))
            .assertNext(result -> {
                assertThat(result.getOutput()).isEqualTo(List.of("alpha", "beta"));
                assertThat(result.getReasoningText()).isEqualTo("Return json.");
            })
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
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("<think>Pick one.</think>sunny", "stop", 2, 4));
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Classify weather")
            .output(OutputSpec.choice(List.of("sunny", "rainy")))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getOutput()).isEqualTo("sunny");
                assertThat(result.getReasoningText()).isEqualTo("Pick one.");
            })
            .verifyComplete();
    }

    @Test
    void generateText_keepsProviderMetadataProviderSpecific() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            chatResponse("Answer.", "stop", 2, 4, Map.of(
                "native", "value",
                "id", "metadata-id",
                "model", "metadata-model",
                "providerType", "metadata-provider",
                "reasoning_content", "metadata reasoning"
            ))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        StepVerifier.create(model.generateText(GenerateTextRequest.builder().prompt("Hi").build()))
            .assertNext(result -> {
                assertThat(result.getProviderMetadata()).containsEntry("native", "value");
                assertThat(result.getProviderMetadata())
                    .doesNotContainKeys("providerType", "id", "model", "reasoning_content",
                        "reasoningContent");
                assertThat(result.getResponse().getId()).isEqualTo("resp_1");
                assertThat(result.getResponse().getModel()).isEqualTo("test-model");
            })
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
    void streamText_repairsInvalidToolInputBeforeEmittingResultAndContinuing() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var repairs = new AtomicInteger();
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(repairableWeatherTool(context -> {
                executions.incrementAndGet();
                return Mono.just(Map.of("temperature", 22));
            })))
            .toolCallRepair(context -> {
                repairs.incrementAndGet();
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        var stream = model.streamText(request);

        StepVerifier.create(stream.fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsSubsequence(
                        PartType.TOOL_CALL,
                        PartType.TOOL_RESULT,
                        PartType.FINISH_STEP,
                        PartType.START_STEP,
                        PartType.TEXT_DELTA
                    );
                assertThat(parts.stream()
                    .filter(part -> PartType.TOOL_CALL.equals(part.getType()))
                    .toList())
                    .singleElement()
                    .satisfies(part -> assertThat(part.getInput())
                        .containsEntry("location", "SF")
                        .doesNotContainKey("city"));
                assertThat(parts.stream()
                    .filter(part -> PartType.FINISH_STEP.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getWarnings())
                    .extracting("code")
                    .contains("tool-call-repaired");
            })
            .verifyComplete();

        StepVerifier.create(stream.textStream().collectList())
            .assertNext(text -> assertThat(String.join("", text)).isEqualTo("It is 22C."))
            .verifyComplete();

        StepVerifier.create(stream.result())
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getToolCalls())
                    .singleElement()
                    .satisfies(call -> assertThat(call.getInput())
                        .containsEntry("location", "SF"));
                assertThat(result.getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .filter(part -> PartType.TOOL_CALL.equals(part.getType()))
                    .toList())
                    .singleElement()
                    .satisfies(part -> assertThat(part.getInput())
                        .containsEntry("location", "SF"));
            })
            .verifyComplete();

        assertThat(repairs).hasValue(1);
        assertThat(executions).hasValue(1);
        verify(chatModel, times(2)).stream(any(Prompt.class));
    }

    @Test
    void streamText_composesAsyncToolExecutorOnNonBlockingThread() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> Mono.defer(() -> Mono.just(Map.of("temperature", 22))))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream()
                .subscribeOn(Schedulers.parallel())
                .collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsSubsequence(PartType.TOOL_CALL, PartType.TOOL_RESULT,
                        PartType.FINISH_STEP, PartType.START_STEP, PartType.TEXT_DELTA);
                assertThat(parts.stream()
                    .filter(part -> PartType.TOOL_RESULT.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getResult())
                    .isEqualTo(Map.of("temperature", 22));
            })
            .verifyComplete();
    }

    @Test
    void streamText_composesAsyncToolRepairOnNonBlockingThread() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22)))))
            .toolCallRepair(context -> Mono.defer(() -> Mono.just(ToolCallRepairResult.repaired(
                ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()))))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream()
                .subscribeOn(Schedulers.parallel())
                .collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsSubsequence(PartType.TOOL_CALL, PartType.TOOL_RESULT);
                assertThat(parts.stream()
                    .filter(part -> PartType.TOOL_CALL.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getInput())
                    .containsEntry("location", "SF")
                    .doesNotContainKey("city");
                assertThat(parts.stream()
                    .filter(part -> PartType.FINISH_STEP.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getWarnings())
                    .extracting("code")
                    .contains("tool-call-repaired");
            })
            .verifyComplete();
    }

    @Test
    void streamText_failedRepairEmitsOriginalToolCallAndToolError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of()))))
            .toolCallRepair(context -> Mono.just(ToolCallRepairResult.unrepaired()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.streamText(request).fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsSubsequence(PartType.TOOL_CALL, PartType.TOOL_ERROR);
                assertThat(parts.stream()
                    .filter(part -> PartType.TOOL_CALL.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getInput())
                    .containsEntry("city", "SF");
                assertThat(parts.stream()
                    .filter(part -> PartType.TOOL_ERROR.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getErrorText())
                    .contains("location");
                assertThat(parts.stream()
                    .filter(part -> PartType.FINISH_STEP.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getWarnings())
                    .extracting("code")
                    .contains("tool-call-repair-failed");
            })
            .verifyComplete();

        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_returnsPendingExternalToolCallWhenToolHasNoExecutor() {
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

        var result = model.streamText(request);

        StepVerifier.create(result.fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsExactly(
                        PartType.START,
                        PartType.START_STEP,
                        PartType.TOOL_CALL,
                        PartType.FINISH_STEP,
                        PartType.FINISH
                    );
                assertThat(parts)
                    .noneMatch(part -> PartType.TOOL_RESULT.equals(part.getType()))
                    .noneMatch(part -> PartType.TOOL_ERROR.equals(part.getType()));
                var finishStep = parts.stream()
                    .filter(part -> PartType.FINISH_STEP.equals(part.getType()))
                    .findFirst()
                    .orElseThrow();
                assertThat(finishStep.getWarnings()).extracting("code")
                    .contains("external-tool-pending");
            })
            .verifyComplete();

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getToolCalls())
                    .singleElement()
                    .satisfies(call -> assertThat(call.getToolCallId()).isEqualTo("call_1"));
                assertThat(finalResult.getToolResults()).isEmpty();
                assertThat(finalResult.getToolErrors()).isEmpty();
                assertThat(finalResult.getResponseMessages())
                    .singleElement()
                    .satisfies(message -> assertThat(message.getContent()).singleElement()
                        .satisfies(part -> assertThat(part.getType())
                            .isEqualTo(PartType.TOOL_CALL)));
            })
            .verifyComplete();

        StepVerifier.create(result.textStream()).verifyComplete();

        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_stopsMixedExecutableAndExternalToolCallsWithoutContinuation() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(multiToolCallResponse(List.of(
                new AssistantMessage.ToolCall("call_1", "function", "weather",
                    "{\"location\":\"SF\"}"),
                new AssistantMessage.ToolCall("call_2", "function", "search",
                    "{\"query\":\"Halo\"}")
            ), 2, 3))
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var executions = new AtomicInteger();

        var request = GenerateTextRequest.builder()
            .prompt("Use tools")
            .tools(List.of(
                ToolDefinition.builder()
                    .name("weather")
                    .executor(context -> {
                        executions.incrementAndGet();
                        return Mono.just(Map.of("temperature", 22));
                    })
                    .build(),
                ToolDefinition.builder()
                    .name("search")
                    .build()
            ))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();
        var result = model.streamText(request);

        StepVerifier.create(result.fullStream().collectList())
            .assertNext(parts -> {
                assertThat(parts).extracting(TextStreamPart::getType)
                    .containsExactly(
                        PartType.START,
                        PartType.START_STEP,
                        PartType.TOOL_CALL,
                        PartType.FINISH_STEP,
                        PartType.FINISH
                    );
                assertThat(parts)
                    .noneMatch(part -> PartType.TOOL_RESULT.equals(part.getType()))
                    .noneMatch(part -> PartType.TOOL_ERROR.equals(part.getType()));
                assertThat(parts.stream()
                    .filter(part -> PartType.FINISH_STEP.equals(part.getType()))
                    .findFirst()
                    .orElseThrow()
                    .getWarnings())
                    .extracting("code")
                    .contains("external-tool-pending");
            })
            .verifyComplete();

        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getToolCalls())
                    .singleElement()
                    .satisfies(call -> {
                        assertThat(call.getToolCallId()).isEqualTo("call_2");
                        assertThat(call.getToolName()).isEqualTo("search");
                    });
                assertThat(finalResult.getToolResults()).isEmpty();
                assertThat(finalResult.getToolErrors()).isEmpty();
                assertThat(finalResult.getResponseMessages())
                    .singleElement()
                    .satisfies(message -> assertThat(message.getContent())
                        .singleElement()
                        .satisfies(part -> assertThat(part.getToolCallId()).isEqualTo("call_2")));
            })
            .verifyComplete();

        assertThat(executions).hasValue(0);
        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_continuesFromExternalToolResult() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.user("Weather in SF?"),
                externalToolCallMessage("call_1", "weather", Map.of("location", "SF")),
                ModelMessage.tool(List.of(ModelMessagePart.toolResult(ToolResult.builder()
                    .toolCallId("call_1")
                    .toolName("weather")
                    .result(Map.of("temperature", 22))
                    .build())))
            ))
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.textStream())
            .expectNext("It is 22C.")
            .verifyComplete();
        StepVerifier.create(result.result())
            .assertNext(finalResult -> {
                assertThat(finalResult.getText()).isEqualTo("It is 22C.");
                assertThat(finalResult.getResponseMessages())
                    .extracting(ModelMessage::getRole)
                    .containsExactly(ModelMessageRole.ASSISTANT);
            })
            .verifyComplete();

        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_continuesFromExternalToolError() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(chatResponse("I could not get the weather.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");

        var request = GenerateTextRequest.builder()
            .messages(List.of(
                ModelMessage.user("Weather in SF?"),
                externalToolCallMessage("call_1", "weather", Map.of("location", "SF")),
                ModelMessage.tool(List.of(ModelMessagePart.toolError(ToolError.builder()
                    .toolCallId("call_1")
                    .toolName("weather")
                    .errorText("weather service unavailable")
                    .build())))
            ))
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        var result = model.streamText(request);

        StepVerifier.create(result.fullStream())
            .expectNextMatches(part -> PartType.START.equals(part.getType()))
            .expectNextMatches(part -> PartType.START_STEP.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_START.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_DELTA.equals(part.getType()))
            .expectNextMatches(part -> PartType.TEXT_END.equals(part.getType()))
            .expectNextMatches(part -> PartType.FINISH_STEP.equals(part.getType()))
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
            .satisfies(message -> {
                assertThat(message).isInstanceOf(DeepSeekAssistantMessage.class);
                var deepSeekMessage = (DeepSeekAssistantMessage) message;
                assertThat(deepSeekMessage.getReasoningContent()).isEqualTo("Need weather data.");
                assertThat(deepSeekMessage.getMetadata())
                    .containsEntry("reasoningContent", "Need weather data.");
            });
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
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("Done");
                assertThat(result.getResponseMessages())
                    .singleElement()
                    .satisfies(message -> {
                        assertThat(message.getRole()).isEqualTo(ModelMessageRole.ASSISTANT);
                        assertThat(message.getContent().getFirst().getText()).isEqualTo("Done");
                    });
            })
            .verifyComplete();

        assertThat(starts).hasValue(1);
        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void streamText_composesAsyncLifecycleCallbacksOnNonBlockingThread() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var events = new ArrayList<String>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .tools(List.of(ToolDefinition.builder()
                .name("weather")
                .executor(context -> Mono.just(Map.of("temperature", 22)))
                .build()))
            .stopWhen(StopCondition.stepCountIs(2))
            .lifecycle(new GenerationLifecycle() {
                @Override
                public Mono<Void> onToolCallStart(GenerationToolCallStartEvent event) {
                    return Mono.defer(() -> {
                        events.add("tool-start:" + event.getToolName());
                        return Mono.empty();
                    });
                }

                @Override
                public Mono<Void> onToolCallFinish(GenerationToolCallFinishEvent event) {
                    return Mono.defer(() -> {
                        events.add("tool-finish:" + event.getToolName());
                        return Mono.empty();
                    });
                }
            })
            .build();

        StepVerifier.create(model.streamText(request).fullStream()
                .subscribeOn(Schedulers.parallel())
                .collectList())
            .assertNext(parts -> assertThat(parts).extracting(TextStreamPart::getType)
                .containsSubsequence(PartType.TOOL_CALL, PartType.TOOL_RESULT))
            .verifyComplete();

        assertThat(events).containsExactly("tool-start:weather", "tool-finish:weather");
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

    private LanguageModelImpl languageModelWithCapabilities(ChatModel chatModel,
        LanguageCapability languageCapability) {
        return new LanguageModelImpl(chatModel, LanguageModelRuntimeComposition.create("openai",
            "gpt-4o", LanguageModelProviderOptions.defaults(), new LanguageModelRuntimeSupport(),
            new MediaResourcePolicy(), new ModelCapabilityMatcher(),
            ModelCapabilities.language(languageCapability), "vision-model", "openai-provider"));
    }

    private ToolDefinition repairableWeatherTool(ToolExecutor executor) {
        return ToolDefinition.builder()
            .name("weather")
            .description("Get weather")
            .inputSchema(weatherInputSchema())
            .executor(executor)
            .build();
    }

    private Map<String, Object> weatherInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "location", Map.of("type", "string")
            ),
            "required", List.of("location")
        );
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
            var nativeUsage = new NativeUsage(new NativeCompletionTokenDetails(reasoningTokens));
            var totalTokens = promptTokens != null && completionTokens != null
                ? promptTokens + completionTokens
                : null;
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens,
                totalTokens, nativeUsage));
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

    private ChatResponse deepSeekReasoningResponse(String reasoning, String text,
        String finishReason) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        var output = DeepSeekAssistantMessage.builder()
            .content(text)
            .reasoningContent(reasoning)
            .build();
        return new ChatResponse(
            List.of(new Generation(output, generationMetadata)),
            ChatResponseMetadata.builder()
                .id("resp_deepseek_reasoning")
                .model("deepseek-reasoner")
                .build()
        );
    }

    private ChatResponse assistantResponse(String text, Map<String, Object> properties,
        String finishReason, Integer promptTokens, Integer completionTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason(finishReason)
            .build();
        var metadataBuilder = ChatResponseMetadata.builder()
            .id("resp_1")
            .model("test-model");
        if (promptTokens != null || completionTokens != null) {
            metadataBuilder.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        var output = AssistantMessage.builder()
            .content(text)
            .properties(properties)
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

    private ChatResponse multiToolCallResponse(List<AssistantMessage.ToolCall> toolCalls,
        Integer promptTokens, Integer completionTokens) {
        var generationMetadata = ChatGenerationMetadata.builder()
            .finishReason("tool_calls")
            .build();
        var output = AssistantMessage.builder()
            .content("")
            .toolCalls(toolCalls)
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

    private ModelMessage externalToolCallMessage(String id, String name, Map<String, Object> input) {
        return ModelMessage.assistant(List.of(ModelMessagePart.toolCall(ToolCall.builder()
            .toolCallId(id)
            .toolName(name)
            .input(input)
            .build())));
    }

    private List<ModelMessage> approvalMessages(boolean approved, String reason) {
        var approvalRequest = ToolApprovalRequest.builder()
            .approvalId("approval_call_1")
            .toolCallId("call_1")
            .toolName("run")
            .input(Map.of("command", "rm file"))
            .stepIndex(0)
            .providerMetadata(Map.of())
            .build();
        var approvalResponse = ToolApprovalResponse.builder()
            .approvalId("approval_call_1")
            .toolCallId("call_1")
            .toolName("run")
            .approved(approved)
            .reason(reason)
            .build();
        return List.of(
            ModelMessage.user("Remove file"),
            ModelMessage.assistant(List.of(
                ModelMessagePart.toolCall(run.halo.aifoundation.tool.ToolCall.builder()
                    .toolCallId("call_1")
                    .toolName("run")
                    .input(Map.of("command", "rm file"))
                    .build()),
                ModelMessagePart.toolApprovalRequest(approvalRequest)
            )),
            ModelMessage.tool(List.of(ModelMessagePart.toolApprovalResponse(approvalResponse)))
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
        return LanguageModelProviderOptions.builder()
            .reasoningHistorySupported(true)
            .streamToolCallsForReasoning(true)
            .requestHeadersSupported(true)
            .seedSupported(true)
            .toolCallingChatOptionsFactory((request, toolCallbacks, toolNames) -> {
                var builder = OpenAiCompatibleChatOptions.builder()
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxOutputTokens())
                    .topP(request.getTopP())
                    .presencePenalty(request.getPresencePenalty())
                    .frequencyPenalty(request.getFrequencyPenalty())
                    .stop(request.getStopSequences())
                    .toolCallbacks(toolCallbacks);
                var providerOptions = request.getProviderOptions() != null
                    ? request.getProviderOptions().get("deepseek")
                    : null;
                if (providerOptions != null && !providerOptions.isEmpty()) {
                    builder.extraBody(Map.copyOf(providerOptions));
                }
                return builder.build();
            })
            .build();
    }

    public record NativeUsage(NativeCompletionTokenDetails completionTokenDetails) {
    }

    public record NativeCompletionTokenDetails(Integer reasoningTokens) {
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
