package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.control.CancellationToken;

/**
 * Options for the framework-neutral UI message chat handler.
 *
 * @param <M> message metadata type
 */
public final class UIMessageChatOptions<M> {
    private LanguageModel model;
    private List<UIMessage<M>> messages;
    private UIMessageChatRequest<M> chatRequest;
    private UIMessage<M> message;
    private Supplier<M> metadataSupplier = () -> null;
    private Supplier<String> messageIdGenerator = () -> "msg_" + UUID.randomUUID();
    private Function<UIMessageChunk, String> serializer;
    private Consumer<GenerateTextRequest.GenerateTextRequestBuilder> requestCustomizer =
        builder -> {
        };
    private Consumer<UIMessageValidationOptions<M>> validationCustomizer = options -> {
    };
    private Consumer<UIMessageConversionOptions<M>> conversionCustomizer = options -> {
    };
    private Consumer<UIMessageStreamFinish<M>> finishHandler = finish -> {
    };
    private Function<Throwable, String> errorHandler = error -> UIMessageStreamOptions
        .DEFAULT_ERROR_TEXT;
    private Consumer<Throwable> readErrorHandler = error -> {
    };
    private CancellationToken cancellationToken;
    private boolean terminateOnError;

    /**
     * Sets the language model used to produce the assistant response.
     *
     * @param model language model
     * @return this options object
     */
    public UIMessageChatOptions<M> model(LanguageModel model) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        return this;
    }

    /**
     * Sets already-normalized persisted UI messages.
     *
     * <p>Use either {@link #messages(List)} or {@link #chatRequest(UIMessageChatRequest)}, not both.
     *
     * @param messages persisted conversation messages
     * @return this options object
     */
    public UIMessageChatOptions<M> messages(List<UIMessage<M>> messages) {
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        return this;
    }

    /**
     * Sets a framework-neutral chat request received from a transport layer.
     *
     * <p>Use either {@link #chatRequest(UIMessageChatRequest)} or {@link #messages(List)}, not both.
     *
     * @param chatRequest transport request
     * @return this options object
     */
    public UIMessageChatOptions<M> chatRequest(UIMessageChatRequest<M> chatRequest) {
        this.chatRequest = Objects.requireNonNull(chatRequest, "chatRequest must not be null");
        return this;
    }

    /**
     * Sets an existing assistant message to continue while streaming.
     *
     * @param message assistant message to continue, or {@code null} for a new response
     * @return this options object
     */
    public UIMessageChatOptions<M> message(UIMessage<M> message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the supplier used to create initial metadata for new assistant responses.
     *
     * @param metadataSupplier metadata supplier
     * @return this options object
     */
    public UIMessageChatOptions<M> metadataSupplier(Supplier<M> metadataSupplier) {
        this.metadataSupplier = Objects.requireNonNull(metadataSupplier,
            "metadataSupplier must not be null");
        return this;
    }

    /**
     * Sets the generator used when the stream does not provide a response message id.
     *
     * @param generator message id generator
     * @return this options object
     */
    public UIMessageChatOptions<M> generateMessageId(Supplier<String> generator) {
        this.messageIdGenerator = Objects.requireNonNull(generator,
            "generator must not be null");
        return this;
    }

    /**
     * Sets the serializer used by {@link UIMessageStreamResponse#body()}.
     *
     * @param serializer chunk serializer
     * @return this options object
     */
    public UIMessageChatOptions<M> serializer(Function<UIMessageChunk, String> serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
        return this;
    }

    /**
     * Customizes the underlying model request.
     *
     * <p>The customizer must not set prompt, messages, or cancellation token because
     * those are owned by the UI message handler.
     *
     * @param customizer model request customizer
     * @return this options object
     */
    public UIMessageChatOptions<M> request(
        Consumer<GenerateTextRequest.GenerateTextRequestBuilder> customizer) {
        this.requestCustomizer = Objects.requireNonNull(customizer,
            "customizer must not be null");
        return this;
    }

    /**
     * Customizes UI message validation before model conversion.
     *
     * @param customizer validation option customizer
     * @return this options object
     */
    public UIMessageChatOptions<M> validation(
        Consumer<UIMessageValidationOptions<M>> customizer) {
        this.validationCustomizer = Objects.requireNonNull(customizer,
            "customizer must not be null");
        return this;
    }

    /**
     * Customizes conversion from UI messages to model messages.
     *
     * @param customizer conversion option customizer
     * @return this options object
     */
    public UIMessageChatOptions<M> conversion(
        Consumer<UIMessageConversionOptions<M>> customizer) {
        this.conversionCustomizer = Objects.requireNonNull(customizer,
            "customizer must not be null");
        return this;
    }

    /**
     * Registers a callback invoked with the aggregated response after the stream finishes.
     *
     * @param handler finish callback
     * @return this options object
     */
    public UIMessageChatOptions<M> onFinish(Consumer<UIMessageStreamFinish<M>> handler) {
        this.finishHandler = Objects.requireNonNull(handler, "handler must not be null");
        return this;
    }

    /**
     * Maps model or writer errors to terminal error text.
     *
     * @param errorHandler error text mapper
     * @return this options object
     */
    public UIMessageChatOptions<M> onError(Function<Throwable, String> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler must not be null");
        return this;
    }

    /**
     * Observes errors thrown while aggregating UI message chunks.
     *
     * @param readErrorHandler read error observer
     * @return this options object
     */
    public UIMessageChatOptions<M> onReadError(Consumer<Throwable> readErrorHandler) {
        this.readErrorHandler = Objects.requireNonNull(readErrorHandler,
            "readErrorHandler must not be null");
        return this;
    }

    /**
     * Sets a request-scoped cancellation token passed to the model request.
     *
     * @param cancellationToken cancellation token
     * @return this options object
     */
    public UIMessageChatOptions<M> cancellationToken(CancellationToken cancellationToken) {
        this.cancellationToken = Objects.requireNonNull(cancellationToken,
            "cancellationToken must not be null");
        return this;
    }

    /**
     * Controls whether aggregation errors are propagated instead of captured.
     *
     * @param terminateOnError whether to propagate read errors
     * @return this options object
     */
    public UIMessageChatOptions<M> terminateOnError(boolean terminateOnError) {
        this.terminateOnError = terminateOnError;
        return this;
    }

    LanguageModel model() {
        return model;
    }

    List<UIMessage<M>> messages() {
        return messages;
    }

    UIMessageChatRequest<M> chatRequest() {
        return chatRequest;
    }

    UIMessage<M> message() {
        return message;
    }

    Supplier<M> metadataSupplier() {
        return metadataSupplier;
    }

    Supplier<String> messageIdGenerator() {
        return messageIdGenerator;
    }

    Function<UIMessageChunk, String> serializer() {
        return serializer;
    }

    Consumer<GenerateTextRequest.GenerateTextRequestBuilder> requestCustomizer() {
        return requestCustomizer;
    }

    Consumer<UIMessageValidationOptions<M>> validationCustomizer() {
        return validationCustomizer;
    }

    Consumer<UIMessageConversionOptions<M>> conversionCustomizer() {
        return conversionCustomizer;
    }

    Consumer<UIMessageStreamFinish<M>> finishHandler() {
        return finishHandler;
    }

    Function<Throwable, String> errorHandler() {
        return errorHandler;
    }

    Consumer<Throwable> readErrorHandler() {
        return readErrorHandler;
    }

    CancellationToken cancellationToken() {
        return cancellationToken;
    }

    boolean terminateOnError() {
        return terminateOnError;
    }
}
