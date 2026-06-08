package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Options for reading a UI message stream into response message snapshots.
 *
 * @param <M> message metadata type
 */
public final class UIMessageStreamReaderOptions<M> {

    private UIMessageStream stream;
    private UIMessage<M> message;
    private List<UIMessage<M>> originalMessages = List.of();
    private Supplier<String> messageIdGenerator = () -> "msg_" + UUID.randomUUID();
    private Supplier<M> metadataSupplier;
    private UIMessageMetadataMerger<M> metadataMerger = UIMessageMetadataMerger.defaults();
    private Function<Throwable, String> errorHandler =
        error -> UIMessageStreamOptions.DEFAULT_ERROR_TEXT;
    private Consumer<Throwable> onError = error -> {
    };
    private boolean terminateOnError;

    /**
     * Creates options with null metadata as the default.
     */
    public UIMessageStreamReaderOptions() {
        this.metadataSupplier = () -> null;
    }

    /**
     * Creates options whose metadata default is an empty map.
     *
     * @return default map-metadata reader options
     */
    public static UIMessageStreamReaderOptions<Map<String, Object>> defaults() {
        return new UIMessageStreamReaderOptions<Map<String, Object>>()
            .metadataSupplier(Map::of);
    }

    /**
     * Sets the stream to aggregate.
     *
     * @param stream UI message stream
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> stream(UIMessageStream stream) {
        this.stream = Objects.requireNonNull(stream, "stream must not be null");
        return this;
    }

    /**
     * Sets an existing assistant message to continue.
     *
     * @param message existing assistant message
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> message(UIMessage<M> message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the original conversation messages for continuation-aware finish handling.
     *
     * @param messages original persisted messages
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> originalMessages(List<UIMessage<M>> messages) {
        this.originalMessages = List.copyOf(Objects.requireNonNull(messages,
            "messages must not be null"));
        return this;
    }

    /**
     * Sets the generator used when the stream does not provide a message id.
     *
     * @param generator message id generator
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> messageIdGenerator(Supplier<String> generator) {
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
    public UIMessageStreamReaderOptions<M> metadataSupplier(Supplier<M> supplier) {
        this.metadataSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
        return this;
    }

    /**
     * Sets how message metadata chunks are merged into typed metadata.
     *
     * @param merger metadata merger
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> metadataMerger(UIMessageMetadataMerger<M> merger) {
        this.metadataMerger = Objects.requireNonNull(merger, "merger must not be null");
        return this;
    }

    /**
     * Maps read errors to terminal error text when errors are captured.
     *
     * @param handler error text mapper
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> errorHandler(Function<Throwable, String> handler) {
        this.errorHandler = Objects.requireNonNull(handler, "handler must not be null");
        return this;
    }

    /**
     * Observes errors thrown while reading and aggregating stream chunks.
     *
     * @param onError read error observer
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> onError(Consumer<Throwable> onError) {
        this.onError = Objects.requireNonNull(onError, "onError must not be null");
        return this;
    }

    /**
     * Controls whether read errors are propagated instead of captured in terminal state.
     *
     * @param terminateOnError whether to propagate read errors
     * @return this options object
     */
    public UIMessageStreamReaderOptions<M> terminateOnError(boolean terminateOnError) {
        this.terminateOnError = terminateOnError;
        return this;
    }

    UIMessageStream stream() {
        return stream;
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

    Consumer<Throwable> onError() {
        return onError;
    }

    boolean terminateOnError() {
        return terminateOnError;
    }
}
