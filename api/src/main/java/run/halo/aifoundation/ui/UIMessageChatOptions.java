package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import run.halo.aifoundation.agent.Agent;
import run.halo.aifoundation.agent.AgentCall;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.control.CancellationToken;
import reactor.core.publisher.Mono;

/**
 * Options for the framework-neutral UI message chat handler.
 *
 * @param <M> message metadata type
 */
public final class UIMessageChatOptions<M> {
    private LanguageModel model;
    private Agent<?> agent;
    private UIMessageAgentExecution agentExecution;
    private List<UIMessage<M>> messages;
    private UIMessageChatRequest<M> chatRequest;
    private UIMessage<M> message;
    private Supplier<M> metadataSupplier = () -> null;
    private Supplier<String> messageIdGenerator = () -> "msg_" + UUID.randomUUID();
    private Function<UIMessageChunk, String> serializer;
    private Consumer<GenerateTextRequest.GenerateTextRequestBuilder> requestCustomizer =
        builder -> {
        };
    private UIMessageChatPrepare<M> prepareHandler = context -> Mono.empty();
    private boolean prepareCustomized;
    private final List<LanguageModelMiddleware> middleware = new ArrayList<>();
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
     * Selects a typed agent and its endpoint-owned call options for this chat response.
     *
     * <p>Use either {@link #model(LanguageModel)} or this method, not both. The options value is
     * passed to {@link AgentCall} after UI messages have been validated and converted.
     *
     * @param agent reusable agent
     * @param callOptions typed call options derived by the endpoint
     * @param <O> agent call options type
     * @return this options object
     */
    public <O> UIMessageChatOptions<M> agent(Agent<O> agent, O callOptions) {
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
        this.agentExecution = (messages, source, middleware) -> agent.stream(
            AgentCall.<O>builder()
                .messages(messages)
                .options(callOptions)
                .metadata(source.getMetadata())
                .context(source.getContext())
                .headers(source.getHeaders())
                .cancellationToken(cancellationToken)
                .timeouts(source.getTimeouts())
                .lifecycle(source.getLifecycle() == null
                    ? List.of() : List.of(source.getLifecycle()))
                .middleware(middleware)
                .build());
        return this;
    }

    /**
     * Selects a no-options agent for this chat response.
     */
    public UIMessageChatOptions<M> agent(Agent<Void> agent) {
        return agent(agent, null);
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
     * Registers an async prepare hook before the final model request is executed.
     *
     * @param prepare prepare hook
     * @return this options object
     */
    public UIMessageChatOptions<M> prepare(UIMessageChatPrepare<M> prepare) {
        this.prepareHandler = Objects.requireNonNull(prepare, "prepare must not be null");
        this.prepareCustomized = true;
        return this;
    }

    /**
     * Adds request-scoped language model middleware.
     *
     * @param middleware middleware entries
     * @return this options object
     */
    public UIMessageChatOptions<M> middleware(LanguageModelMiddleware... middleware) {
        if (middleware != null) {
            for (var entry : middleware) {
                if (entry != null) {
                    this.middleware.add(entry);
                }
            }
        }
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

    Agent<?> agent() {
        return agent;
    }

    UIMessageAgentExecution agentExecution() {
        return agentExecution;
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

    UIMessageChatPrepare<M> prepareHandler() {
        return prepareHandler;
    }

    boolean prepareCustomized() {
        return prepareCustomized;
    }

    List<LanguageModelMiddleware> middleware() {
        return List.copyOf(middleware);
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

    @FunctionalInterface
    interface UIMessageAgentExecution {
        run.halo.aifoundation.chat.StreamTextResult stream(
            List<run.halo.aifoundation.message.ModelMessage> messages,
            GenerateTextRequest source,
            List<LanguageModelMiddleware> middleware);
    }
}
