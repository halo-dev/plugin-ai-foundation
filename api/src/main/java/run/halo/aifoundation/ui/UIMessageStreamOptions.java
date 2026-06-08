package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import run.halo.aifoundation.control.CancellationToken;

/**
 * Options for creating a Halo UI message stream.
 *
 * @param <M> message metadata type
 */
public final class UIMessageStreamOptions<M> {

    static final String DEFAULT_ERROR_TEXT = "An error occurred.";

    private String messageId;
    private UIMessage<M> message;
    private List<UIMessage<M>> originalMessages = List.of();
    private Supplier<String> messageIdGenerator = () -> "msg_" + UUID.randomUUID();
    private Supplier<M> metadataSupplier;
    private UIMessageMetadataMerger<M> metadataMerger = UIMessageMetadataMerger.defaults();
    private Function<Throwable, String> errorHandler = error -> DEFAULT_ERROR_TEXT;
    private Consumer<Throwable> readErrorHandler = error -> {
    };
    private CancellationToken cancellationToken;
    private boolean terminateOnError;
    private Consumer<UIMessageStreamFinish<M>> finishHandler = finish -> {
    };
    private Consumer<UIMessageStreamWriter> execute = writer -> {
    };

    /**
     * Creates options with null metadata as the default.
     */
    public UIMessageStreamOptions() {
        this.metadataSupplier = () -> null;
    }

    /**
     * Creates options whose metadata default is an empty map.
     *
     * @return default map-metadata options
     */
    public static UIMessageStreamOptions<Map<String, Object>> defaults() {
        return new UIMessageStreamOptions<Map<String, Object>>()
            .metadataSupplier(Map::of);
    }

    /**
     * Sets the assistant message id for the generated response.
     *
     * @param messageId response message id
     * @return this options object
     */
    public UIMessageStreamOptions<M> messageId(String messageId) {
        this.messageId = messageId;
        this.messageIdGenerator = () -> messageId;
        return this;
    }

    /**
     * Sets an existing assistant message to continue.
     *
     * @param message existing assistant message, or {@code null} for a new response
     * @return this options object
     */
    public UIMessageStreamOptions<M> message(UIMessage<M> message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the conversation messages used by the finish callback.
     *
     * @param messages original persisted messages
     * @return this options object
     */
    public UIMessageStreamOptions<M> originalMessages(List<UIMessage<M>> messages) {
        this.originalMessages = List.copyOf(Objects.requireNonNull(messages,
            "messages must not be null"));
        return this;
    }

    /**
     * Sets the generator used when no message id is provided by the stream.
     *
     * @param generator message id generator
     * @return this options object
     */
    public UIMessageStreamOptions<M> generateMessageId(Supplier<String> generator) {
        this.messageIdGenerator = Objects.requireNonNull(generator,
            "generator must not be null");
        return this;
    }

    /**
     * Sets the supplier used to create initial metadata for a new response message.
     *
     * @param supplier metadata supplier
     * @return this options object
     */
    public UIMessageStreamOptions<M> metadataSupplier(Supplier<M> supplier) {
        this.metadataSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
        return this;
    }

    /**
     * Sets how message metadata chunks are merged into typed metadata.
     *
     * @param merger metadata merger
     * @return this options object
     */
    public UIMessageStreamOptions<M> metadataMerger(UIMessageMetadataMerger<M> merger) {
        this.metadataMerger = Objects.requireNonNull(merger, "merger must not be null");
        return this;
    }

    /**
     * Maps execution or merged-stream failures to terminal error text.
     *
     * @param errorHandler error text mapper
     * @return this options object
     */
    public UIMessageStreamOptions<M> onError(Function<Throwable, String> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler,
            "errorHandler must not be null");
        return this;
    }

    /**
     * Observes errors thrown while reading and aggregating stream chunks.
     *
     * @param readErrorHandler read error observer
     * @return this options object
     */
    public UIMessageStreamOptions<M> onReadError(Consumer<Throwable> readErrorHandler) {
        this.readErrorHandler = Objects.requireNonNull(readErrorHandler,
            "readErrorHandler must not be null");
        return this;
    }

    /**
     * Sets a cancellation token that maps cancellation failures to abort chunks.
     *
     * @param cancellationToken request-scoped cancellation token
     * @return this options object
     */
    public UIMessageStreamOptions<M> cancellationToken(CancellationToken cancellationToken) {
        this.cancellationToken = Objects.requireNonNull(cancellationToken,
            "cancellationToken must not be null");
        return this;
    }

    /**
     * Controls whether reader aggregation errors terminate the stream instead of being captured.
     *
     * @param terminateOnError whether to propagate read errors
     * @return this options object
     */
    public UIMessageStreamOptions<M> terminateOnError(boolean terminateOnError) {
        this.terminateOnError = terminateOnError;
        return this;
    }

    /**
     * Registers a callback invoked with the aggregated response after the stream finishes.
     *
     * @param finishHandler finish callback
     * @return this options object
     */
    public UIMessageStreamOptions<M> onFinish(Consumer<UIMessageStreamFinish<M>> finishHandler) {
        this.finishHandler = Objects.requireNonNull(finishHandler,
            "finishHandler must not be null");
        return this;
    }

    /**
     * Sets the writer callback that produces or merges chunks.
     *
     * @param execute writer callback
     * @return this options object
     */
    public UIMessageStreamOptions<M> execute(Consumer<UIMessageStreamWriter> execute) {
        this.execute = Objects.requireNonNull(execute, "execute must not be null");
        return this;
    }

    String messageId() {
        return messageId;
    }

    UIMessage<M> message() {
        return message;
    }

    List<UIMessage<M>> originalMessages() {
        return originalMessages;
    }

    Supplier<String> messageIdGenerator() {
        return messageIdGenerator;
    }

    Supplier<M> metadataSupplier() {
        return metadataSupplier;
    }

    UIMessageMetadataMerger<M> metadataMerger() {
        return metadataMerger;
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

    Consumer<UIMessageStreamFinish<M>> finishHandler() {
        return finishHandler;
    }

    Consumer<UIMessageStreamWriter> execute() {
        return execute;
    }
}
