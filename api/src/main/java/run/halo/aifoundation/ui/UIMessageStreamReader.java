package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads a UI message chunk stream into assistant message snapshots.
 *
 * <p>Example:
 * <pre>{@code
 * ReadUIMessageStreamResult<MyMetadata> result = UIMessageStreamReader.read(options -> options
 *     .stream(stream)
 *     .message(existingAssistantMessage)
 *     .metadataSupplier(MyMetadata::empty));
 *
 * Flux<UIMessage<MyMetadata>> snapshots = result.messages();
 * Mono<UIMessage<MyMetadata>> finalMessage = result.responseMessage();
 * }</pre>
 */
public final class UIMessageStreamReader {

    private UIMessageStreamReader() {
    }

    /**
     * Reads a stream with map metadata defaults.
     *
     * @param stream stream to aggregate
     * @return reader result containing snapshots, final response message, and terminal state
     */
    public static ReadUIMessageStreamResult<Map<String, Object>> read(UIMessageStream stream) {
        return read(options -> options.stream(stream).metadataSupplier(Map::of));
    }

    /**
     * Reads a stream as a continuation of an existing assistant message.
     *
     * @param message existing assistant message to continue
     * @param stream stream to aggregate
     * @param <M> message metadata type
     * @return reader result containing snapshots, final response message, and terminal state
     */
    public static <M> ReadUIMessageStreamResult<M> read(UIMessage<M> message,
        UIMessageStream stream) {
        return read(options -> options.message(message).stream(stream));
    }

    /**
     * Reads a stream with full aggregation options.
     *
     * @param configure option customizer
     * @param <M> message metadata type
     * @return reader result containing snapshots, final response message, and terminal state
     */
    public static <M> ReadUIMessageStreamResult<M> read(
        Consumer<UIMessageStreamReaderOptions<M>> configure) {
        Objects.requireNonNull(configure, "configure must not be null");
        var options = new UIMessageStreamReaderOptions<M>();
        configure.accept(options);
        if (options.stream() == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        return createResult(options);
    }

    private static <M> ReadUIMessageStreamResult<M> createResult(
        UIMessageStreamReaderOptions<M> options) {
        var events = Flux.defer(() -> {
            var state = new State<>(options);
            return options.stream()
                .chunks()
                .handle((chunk, sink) -> {
                    var message = state.accept(chunk);
                    if (message != null) {
                        sink.next(ReadEvent.snapshot(message));
                    }
                })
                .cast(ReadEvent.class)
                .onErrorResume(error -> {
                    options.onError().accept(error);
                    if (options.terminateOnError()) {
                        return Flux.error(error);
                    }
                    state.recordReadError(options.errorHandler().apply(error));
                    return Flux.empty();
                })
                .concatWith(Mono.fromSupplier(() -> ReadEvent.terminal(
                    state.responseMessage(), state.terminal())));
        }).cache();

        var snapshots = events
            .filter(ReadEvent::snapshot)
            .map(ReadEvent::message);
        var terminal = events
            .filter(event -> !event.snapshot())
            .last()
            .cache();

        return new ReadUIMessageStreamResult<>(
            snapshots.map(message -> (UIMessage<M>) message),
            terminal.map(event -> (UIMessage<M>) event.message()),
            terminal.map(ReadEvent::terminal)
        );
    }

    private record ReadEvent(boolean snapshot, UIMessage<?> message,
                             UIMessageStreamTerminal terminal) {
        static ReadEvent snapshot(UIMessage<?> message) {
            return new ReadEvent(true, message, null);
        }

        static ReadEvent terminal(UIMessage<?> message, UIMessageStreamTerminal terminal) {
            return new ReadEvent(false, message, terminal);
        }
    }

    private static final class State<M> {
        private final UIMessageStreamReaderOptions<M> options;
        private final UIMessageChunkReducer reducer;
        private String messageId;
        private M metadata;
        private boolean metadataCreated;

        State(UIMessageStreamReaderOptions<M> options) {
            this.options = options;
            var existing = options.message();
            this.messageId = existing != null ? existing.id() : null;
            this.reducer = new UIMessageChunkReducer(existing != null ? existing.parts() : List.of());
            if (existing != null) {
                this.metadata = existing.metadata();
                this.metadataCreated = true;
            }
        }

        UIMessage<M> accept(UIMessageChunk chunk) {
            if (chunk == null) {
                return null;
            }
            var changed = reducer.accept(chunk);
            changed = switch (chunk) {
                case StartChunk start -> {
                    if (messageId == null && start.messageId() != null
                        && !start.messageId().isBlank()) {
                        messageId = start.messageId();
                    }
                    yield changed || mergeMetadata(start.messageMetadata());
                }
                case TextStartChunk ignored -> changed;
                case TextDeltaChunk ignored -> changed;
                case TextEndChunk ignored -> changed;
                case ReasoningStartChunk ignored -> changed;
                case ReasoningDeltaChunk ignored -> changed;
                case ReasoningEndChunk ignored -> changed;
                case DataChunk ignored -> changed;
                case MessageMetadataChunk metadata -> mergeMetadata(metadata.messageMetadata());
                case SourceUrlChunk ignored -> changed;
                case SourceDocumentChunk ignored -> changed;
                case FileChunk ignored -> changed;
                case ToolInputStartChunk ignored -> changed;
                case ToolInputDeltaChunk ignored -> changed;
                case ToolInputAvailableChunk ignored -> changed;
                case ToolOutputAvailableChunk ignored -> changed;
                case ToolOutputErrorChunk ignored -> changed;
                case ToolApprovalRequestChunk ignored -> changed;
                case ToolApprovalResponseChunk ignored -> changed;
                case ToolChunk ignored -> changed;
                case FinishChunk finish -> {
                    yield changed || mergeMetadata(finish.messageMetadata());
                }
                case ErrorChunk ignored -> changed;
                case AbortChunk ignored -> changed;
                case StartStepChunk ignored -> changed;
                case FinishStepChunk ignored -> changed;
            };
            return changed ? responseMessage() : null;
        }

        void recordReadError(String errorText) {
            reducer.recordReadError(errorText);
        }

        UIMessage<M> responseMessage() {
            if (messageId == null || messageId.isBlank()) {
                messageId = options.messageIdGenerator().get();
            }
            if (!metadataCreated) {
                metadata = options.metadataSupplier().get();
                metadataCreated = true;
            }
            return new UIMessage<>(messageId, UIMessageRole.ASSISTANT, reducer.parts(),
                metadata);
        }

        UIMessageStreamTerminal terminal() {
            return reducer.terminal();
        }

        private boolean mergeMetadata(Object update) {
            if (update == null) {
                return false;
            }
            if (!metadataCreated) {
                metadata = options.metadataSupplier().get();
                metadataCreated = true;
            }
            var merged = options.metadataMerger().merge(metadata, update);
            if (Objects.equals(metadata, merged)) {
                return false;
            }
            metadata = merged;
            return true;
        }
    }
}
