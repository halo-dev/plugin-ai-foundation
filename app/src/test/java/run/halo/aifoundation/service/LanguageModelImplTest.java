package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.aifoundation.FinishReason;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.ModelMessage;
import run.halo.aifoundation.ModelMessagePart;
import run.halo.aifoundation.ModelMessageRole;
import run.halo.aifoundation.TextStreamPart;

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
}
