package run.halo.aifoundation.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.part.TextStreamPart;
import org.junit.jupiter.api.Test;

class UIMessageChatHandlerTest {

    record Metadata(String chatId) {
    }

    @Test
    void streamsModelOutputAndExposesResponseFinishValidationAndConversion() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.start("assistant-1"),
            TextStreamPart.textStart("text-1"),
            TextStreamPart.textDelta("text-1", "Hello"),
            TextStreamPart.textEnd("text-1"),
            TextStreamPart.finish(null, null, null)
        ));
        var user = new UIMessage<>("user-1", UIMessageRole.USER,
            List.of(UIMessageParts.text("user-text", "Hi")), new Metadata("chat-1"));
        var capturedFinish = new AtomicReference<UIMessageStreamFinish<Metadata>>();

        var chat = UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(user))
            .serializer(chunk -> chunk.type())
            .metadataSupplier(() -> new Metadata("chat-1"))
            .request(request -> request.system("You are concise.").maxOutputTokens(128))
            .onFinish(capturedFinish::set));

        var body = chat.response().body().collectList().block();
        var finish = chat.finish().block();

        assertThat(model.streamTextCalls).hasValue(1);
        assertThat(model.capturedRequest.getSystem()).isEqualTo("You are concise.");
        assertThat(model.capturedRequest.getMaxOutputTokens()).isEqualTo(128);
        assertThat(model.capturedRequest.getMessages()).hasSize(1);
        assertThat(model.capturedRequest.getMessages().getFirst().getContent().getFirst().getText())
            .isEqualTo("Hi");
        assertThat(chat.validation().isValid()).isTrue();
        assertThat(chat.conversion().warnings()).isEmpty();
        assertThat(body).contains("data: text-delta\n\n", "data: [DONE]\n\n");
        assertThat(finish.responseMessage().text()).isEqualTo("Hello");
        assertThat(finish.messages()).hasSize(2);
        assertThat(capturedFinish.get()).isEqualTo(finish);
    }

    @Test
    void conversionWarningsDoNotBlockModelInvocation() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "ok"),
            TextStreamPart.finish(null, null, null)
        ));
        var messages = List.of(new UIMessage<>("user-1", UIMessageRole.USER, List.of(
            UIMessageParts.text("text", "Hi"),
            UIMessageParts.data("status", "visible only")
        ), new Metadata("chat-1")));

        var chat = UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(messages));

        chat.response().stream().collectList().block();

        assertThat(model.streamTextCalls).hasValue(1);
        assertThat(chat.conversion().warnings()).extracting(UIMessageConversionWarning::code)
            .contains("data.converter-missing");
    }

    @Test
    void invalidUiMessagesFailBeforeModelInvocation() {
        var model = new FakeLanguageModel(List.of());

        assertThatThrownBy(() -> UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(new UIMessage<>("", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))))))
            .isInstanceOf(InvalidUIMessageException.class);

        assertThat(model.streamTextCalls).hasValue(0);
    }

    @Test
    void emptyConvertedModelMessagesFailBeforeModelInvocation() {
        var model = new FakeLanguageModel(List.of());

        assertThatThrownBy(() -> UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(new UIMessage<>("ui-only", UIMessageRole.USER,
                List.of(UIMessageParts.data("status", "only")), new Metadata("chat"))))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("produced no model messages");

        assertThat(model.streamTextCalls).hasValue(0);
    }

    @Test
    void requestCustomizerMustNotSetPromptOrMessages() {
        var model = new FakeLanguageModel(List.of());
        var messages = List.of(new UIMessage<>("user", UIMessageRole.USER,
            List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat")));
        var cancellation = new CancellationSource();

        assertThatThrownBy(() -> UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(messages)
            .request(request -> request.prompt("nope"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not set prompt");

        assertThatThrownBy(() -> UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(messages)
            .request(request -> request.messages(List.of(ModelMessage.user("nope"))))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not set messages");

        assertThatThrownBy(() -> UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(messages)
            .request(request -> request.cancellationToken(cancellation.token()))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not set cancellationToken");

        assertThat(model.streamTextCalls).hasValue(0);
    }

    @Test
    void chatOptionsInjectCancellationTokenIntoGeneratedRequest() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "ok"),
            TextStreamPart.finish(null, null, null)
        ));
        var source = new CancellationSource();

        UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))))
            .cancellationToken(source.token()));

        assertThat(model.capturedRequest.getCancellationToken()).isSameAs(source.token());
    }

    @Test
    void chatOptionsDoNotCreateCancellationTokenByDefault() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "ok"),
            TextStreamPart.finish(null, null, null)
        ));

        UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat")))));

        assertThat(model.capturedRequest.getCancellationToken()).isNull();
    }

    @Test
    void preservesRequestFieldsAndSupportsValidationConversionCustomizers() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "ok"),
            TextStreamPart.finish(null, null, null)
        ));
        var messages = List.of(new UIMessage<>("user", UIMessageRole.USER, List.of(
            UIMessageParts.data("prompt", "from data")
        ), new Metadata("chat")));

        var chat = UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(messages)
            .validation(validation -> validation.dataValidator("prompt",
                (message, part, context) -> List.of()))
            .conversion(conversion -> conversion.dataConverter("prompt",
                (part, context) -> List.of(run.halo.aifoundation.message.ModelMessagePart.text(
                    part.data().toString()))))
            .request(request -> request
                .system("system")
                .temperature(0.3)
                .topP(0.9)
                .metadata(Map.of("k", "v"))));

        chat.response().stream().collectList().block();

        assertThat(model.capturedRequest.getSystem()).isEqualTo("system");
        assertThat(model.capturedRequest.getTemperature()).isEqualTo(0.3);
        assertThat(model.capturedRequest.getTopP()).isEqualTo(0.9);
        assertThat(model.capturedRequest.getMetadata()).isEqualTo(Map.of("k", "v"));
        assertThat(model.capturedRequest.getMessages().getFirst().getContent().getFirst().getText())
            .isEqualTo("from data");
    }

    @Test
    void wiresOriginalMessagesContinuationMetadataAndGeneratedId() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", " more"),
            TextStreamPart.finish(null, null, null)
        ));
        var assistant = new UIMessage<>("assistant-existing", UIMessageRole.ASSISTANT,
            List.of(UIMessageParts.text("existing-text", "old")), new Metadata("old-chat"));

        var chat = UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(assistant))
            .message(assistant)
            .metadataSupplier(() -> new Metadata("new-chat"))
            .generateMessageId(() -> "generated"));

        chat.response().stream().collectList().block();
        var finish = chat.finish().block();

        assertThat(finish.isContinuation()).isTrue();
        assertThat(finish.messages()).hasSize(1);
        assertThat(finish.responseMessage().id()).isEqualTo("assistant-existing");
        assertThat(finish.responseMessage().text()).isEqualTo("old more");
        assertThat(finish.responseMessage().metadata()).isEqualTo(new Metadata("old-chat"));
    }

    @Test
    void finishFailsWhenOnFinishThrows() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "ok"),
            TextStreamPart.finish(null, null, null)
        ));

        var chat = UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))))
            .onFinish(finish -> {
                throw new IllegalStateException("save failed");
            }));

        chat.response().stream().collectList().block();

        assertThatThrownBy(() -> chat.finish().block())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("save failed");
    }

    @Test
    void cancellationAbortStillInvokesOnFinishWithPartialMessage() {
        var model = new FakeLanguageModel(Flux.concat(
            Flux.just(
                TextStreamPart.textDelta("text-1", "partial")
            ),
            Flux.error(new AiGenerationCancelledException("cancelled"))
        ));
        var capturedFinish = new AtomicReference<UIMessageStreamFinish<Metadata>>();

        var chat = UIMessageChatHandlers.<Metadata>streamText(options -> options
            .model(model)
            .messages(List.of(new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))))
            .onFinish(capturedFinish::set));

        var chunks = chat.response().stream().collectList().block();
        var finish = chat.finish().block();

        assertThat(chunks).extracting(UIMessageChunk::type)
            .containsExactly(UIMessageChunkType.TEXT_DELTA, UIMessageChunkType.ABORT);
        assertThat(finish.responseMessage().text()).isEqualTo("partial");
        assertThat(finish.terminal().aborted()).isTrue();
        assertThat(finish.terminal().errorText()).isNull();
        assertThat(capturedFinish.get()).isEqualTo(finish);
    }

    @Test
    void submitChatRequestStreamsFromProvidedHistory() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "answer"),
            TextStreamPart.finish(null, null, null)
        ));
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))
        ), UIMessageChatTrigger.SUBMIT_MESSAGE, null);

        var chat = UIMessageChatHandlers.streamText(model, request, options -> options
            .serializer(UIMessageChunk::type));

        var body = chat.response().body().collectList().block();

        assertThat(model.streamTextCalls).hasValue(1);
        assertThat(model.capturedRequest.getMessages()).hasSize(1);
        assertThat(model.capturedRequest.getMessages().getFirst().getContent().getFirst().getText())
            .isEqualTo("Hi");
        assertThat(chat.validation().messages()).hasSize(1);
        assertThat(body).contains("data: text-delta\n\n");
    }

    @Test
    void regenerateChatRequestRequiresMessageId() {
        var model = new FakeLanguageModel(List.of());
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))
        ), UIMessageChatTrigger.REGENERATE_MESSAGE, null);

        assertThatThrownBy(() -> UIMessageChatHandlers.streamText(model, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("messageId must be set");

        assertThat(model.streamTextCalls).hasValue(0);
    }

    @Test
    void regenerateChatRequestRejectsUnknownMessageId() {
        var model = new FakeLanguageModel(List.of());
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))
        ), UIMessageChatTrigger.REGENERATE_MESSAGE, "assistant-missing");

        assertThatThrownBy(() -> UIMessageChatHandlers.streamText(model, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("target message not found");

        assertThat(model.streamTextCalls).hasValue(0);
    }

    @Test
    void regenerateChatRequestRejectsNonAssistantMessageId() {
        var model = new FakeLanguageModel(List.of());
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))
        ), UIMessageChatTrigger.REGENERATE_MESSAGE, "user");

        assertThatThrownBy(() -> UIMessageChatHandlers.streamText(model, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("target must be an assistant message");

        assertThat(model.streamTextCalls).hasValue(0);
    }

    @Test
    void regenerateChatRequestTrimsOldAssistantAndLaterMessages() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "new answer"),
            TextStreamPart.finish(null, null, null)
        ));
        var user = new UIMessage<>("user-1", UIMessageRole.USER,
            List.of(UIMessageParts.text("user-text", "Question")), new Metadata("chat"));
        var oldAssistant = new UIMessage<>("assistant-1", UIMessageRole.ASSISTANT,
            List.of(UIMessageParts.text("old-text", "Old answer")), new Metadata("chat"));
        var laterUser = new UIMessage<>("user-2", UIMessageRole.USER,
            List.of(UIMessageParts.text("later-text", "Later question")), new Metadata("chat"));
        var request = new UIMessageChatRequest<>("chat-1",
            List.of(user, oldAssistant, laterUser), UIMessageChatTrigger.REGENERATE_MESSAGE,
            "assistant-1");

        var chat = UIMessageChatHandlers.streamText(model, request);
        chat.response().stream().collectList().block();
        var finish = chat.finish().block();

        assertThat(model.streamTextCalls).hasValue(1);
        assertThat(model.capturedRequest.getMessages()).hasSize(1);
        assertThat(model.capturedRequest.getMessages().getFirst().getContent().getFirst().getText())
            .isEqualTo("Question");
        assertThat(chat.validation().messages()).containsExactly(user);
        assertThat(finish.messages()).hasSize(2);
        assertThat(finish.messages().getFirst()).isEqualTo(user);
        assertThat(finish.responseMessage().text()).isEqualTo("new answer");
        assertThat(finish.messages().get(1)).isEqualTo(finish.responseMessage());
        assertThat(finish.isContinuation()).isFalse();
    }

    @Test
    void chatRequestPreservesRequestCustomizerAndMaxRetriesAsGenerationSetting() {
        var model = new FakeLanguageModel(List.of(
            TextStreamPart.textDelta("text-1", "answer"),
            TextStreamPart.finish(null, null, null)
        ));
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("user", UIMessageRole.USER,
                List.of(UIMessageParts.text("text", "Hi")), new Metadata("chat"))
        ), UIMessageChatTrigger.SUBMIT_MESSAGE, null);

        var chat = UIMessageChatHandlers.streamText(model, request, options -> options
            .request(builder -> builder.maxRetries(3).temperature(0.4)));

        chat.response().stream().collectList().block();

        assertThat(model.streamTextCalls).hasValue(1);
        assertThat(model.capturedRequest.getMaxRetries()).isEqualTo(3);
        assertThat(model.capturedRequest.getTemperature()).isEqualTo(0.4);
    }

    @Test
    void autoReasoningConversionDropsReasoningWhenModelDoesNotSupportHistory() {
        var model = new FakeLanguageModel(List.of(TextStreamPart.finish(null, null, null)));
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.reasoning("reasoning", "think", Map.of("provider", "state")),
                UIMessageParts.text("text", "answer")
            ), new Metadata("chat"))
        ), UIMessageChatTrigger.SUBMIT_MESSAGE, null);

        var chat = UIMessageChatHandlers.streamText(model, request);

        chat.response().stream().collectList().block();

        assertThat(model.capturedRequest.getMessages().getFirst().getContent())
            .extracting(run.halo.aifoundation.message.ModelMessagePart::getType)
            .containsExactly("text");
    }

    @Test
    void autoReasoningConversionPreservesReasoningWhenModelSupportsHistory() {
        var model = new FakeLanguageModel(
            Flux.just(TextStreamPart.finish(null, null, null)),
            LanguageModelCapabilities.supportsReasoningHistory()
        );
        var request = new UIMessageChatRequest<>("chat-1", List.of(
            new UIMessage<>("assistant", UIMessageRole.ASSISTANT, List.of(
                UIMessageParts.reasoning("reasoning", "think", Map.of("provider", "state")),
                UIMessageParts.text("text", "answer")
            ), new Metadata("chat"))
        ), UIMessageChatTrigger.SUBMIT_MESSAGE, null);

        var chat = UIMessageChatHandlers.streamText(model, request);

        chat.response().stream().collectList().block();

        assertThat(model.capturedRequest.getMessages().getFirst().getContent())
            .extracting(run.halo.aifoundation.message.ModelMessagePart::getType)
            .containsExactly("reasoning", "text");
    }

    private static final class FakeLanguageModel implements LanguageModel {
        private final Flux<TextStreamPart> parts;
        private final LanguageModelCapabilities capabilities;
        private final AtomicInteger streamTextCalls = new AtomicInteger();
        private GenerateTextRequest capturedRequest;

        FakeLanguageModel(List<TextStreamPart> parts) {
            this(Flux.fromIterable(parts));
        }

        FakeLanguageModel(Flux<TextStreamPart> parts) {
            this(parts, LanguageModelCapabilities.defaults());
        }

        FakeLanguageModel(Flux<TextStreamPart> parts, LanguageModelCapabilities capabilities) {
            this.parts = parts;
            this.capabilities = capabilities;
        }

        @Override
        public Mono<GenerateTextResult> generateText(String prompt) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public StreamTextResult streamText(GenerateTextRequest request) {
            streamTextCalls.incrementAndGet();
            capturedRequest = request;
            return new StreamTextResult(
                parts,
                Flux.empty(),
                Flux.empty(),
                Flux.empty(),
                Mono.empty(),
                Mono.just(GenerateTextResult.builder().build())
            );
        }

        @Override
        public LanguageModelCapabilities capabilities() {
            return capabilities;
        }
    }
}
