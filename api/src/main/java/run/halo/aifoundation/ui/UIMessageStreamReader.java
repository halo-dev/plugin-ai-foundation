package run.halo.aifoundation.ui;

import java.util.ArrayList;
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
        private final ArrayList<UIMessagePart> parts;
        private UIMessageStreamTerminal terminal = UIMessageStreamTerminal.empty();
        private String messageId;
        private M metadata;
        private boolean metadataCreated;

        State(UIMessageStreamReaderOptions<M> options) {
            this.options = options;
            var existing = options.message();
            this.messageId = existing != null ? existing.id() : null;
            this.parts = new ArrayList<>(existing != null ? existing.parts() : List.of());
            if (existing != null) {
                this.metadata = existing.metadata();
                this.metadataCreated = true;
            }
        }

        UIMessage<M> accept(UIMessageChunk chunk) {
            if (chunk == null) {
                return null;
            }
            var changed = switch (chunk) {
                case StartChunk start -> {
                    if (messageId == null && start.messageId() != null
                        && !start.messageId().isBlank()) {
                        messageId = start.messageId();
                    }
                    yield mergeMetadata(start.messageMetadata());
                }
                case TextStartChunk text -> ensureTextPart(text.id(), "");
                case TextDeltaChunk text -> appendText(text.id(), text.delta());
                case TextEndChunk ignored -> false;
                case ReasoningStartChunk reasoning -> ensureReasoningPart(reasoning.id(), "",
                    null);
                case ReasoningDeltaChunk reasoning -> appendReasoning(reasoning.id(),
                    reasoning.delta(), reasoning.providerMetadata());
                case ReasoningEndChunk ignored -> false;
                case DataChunk data -> !data.transientData()
                    && replaceData(data.name(), data.data());
                case MessageMetadataChunk metadata -> mergeMetadata(metadata.messageMetadata());
                case SourceUrlChunk source -> replaceSource(source);
                case FileChunk file -> replaceFile(file);
                case ToolCallChunk tool -> replaceToolCall(tool);
                case ToolResultChunk tool -> replaceToolResult(tool);
                case ToolErrorChunk tool -> replaceToolError(tool);
                case ToolApprovalRequestChunk request -> replaceToolApprovalRequest(request);
                case FinishChunk finish -> {
                    terminal = terminal.withFinish(finish.finishReason(), finish.usage());
                    yield mergeMetadata(finish.messageMetadata());
                }
                case ErrorChunk error -> {
                    terminal = terminal.withErrorText(error.errorText());
                    yield false;
                }
                case AbortChunk ignored -> {
                    terminal = terminal.withAborted(true);
                    yield false;
                }
                case FinishStepChunk ignored -> false;
                case ToolInputStartChunk ignored -> false;
                case ToolInputDeltaChunk ignored -> false;
            };
            return changed ? responseMessage() : null;
        }

        void recordReadError(String errorText) {
            terminal = terminal.withErrorText(errorText);
        }

        UIMessage<M> responseMessage() {
            if (messageId == null || messageId.isBlank()) {
                messageId = options.messageIdGenerator().get();
            }
            if (!metadataCreated) {
                metadata = options.metadataSupplier().get();
                metadataCreated = true;
            }
            return new UIMessage<>(messageId, UIMessageRole.ASSISTANT, List.copyOf(parts),
                metadata);
        }

        UIMessageStreamTerminal terminal() {
            return terminal;
        }

        private boolean ensureTextPart(String id, String text) {
            var index = indexOfText(id);
            if (index >= 0) {
                return false;
            }
            parts.add(UIMessageParts.text(id, text));
            return true;
        }

        private boolean appendText(String id, String delta) {
            var index = indexOfText(id);
            if (index < 0) {
                parts.add(UIMessageParts.text(id, delta != null ? delta : ""));
            } else {
                var current = (TextPart) parts.get(index);
                parts.set(index, UIMessageParts.text(id, current.text() + (delta != null
                    ? delta
                    : "")));
            }
            return true;
        }

        private boolean ensureReasoningPart(String id, String text,
            Map<String, Object> providerMetadata) {
            var index = indexOfReasoning(id);
            if (index >= 0) {
                return false;
            }
            parts.add(UIMessageParts.reasoning(id, text, providerMetadata));
            return true;
        }

        private boolean appendReasoning(String id, String delta,
            Map<String, Object> providerMetadata) {
            var index = indexOfReasoning(id);
            if (index < 0) {
                parts.add(UIMessageParts.reasoning(id, delta != null ? delta : "",
                    providerMetadata));
            } else {
                var current = (ReasoningPart) parts.get(index);
                parts.set(index, UIMessageParts.reasoning(id,
                    current.text() + (delta != null ? delta : ""), providerMetadata));
            }
            return true;
        }

        private boolean replaceData(String name, Object data) {
            replace(part -> part instanceof DataPart value && name.equals(value.name()),
                UIMessageParts.data(name, data));
            return true;
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

        private boolean replaceSource(SourceUrlChunk source) {
            replace(part -> part instanceof SourceUrlPart value
                    && source.sourceId().equals(value.sourceId()),
                UIMessageParts.sourceUrl(source.sourceId(), source.url(), source.title(),
                    source.providerMetadata()));
            return true;
        }

        private boolean replaceFile(FileChunk file) {
            replace(part -> part instanceof FilePart value && file.fileId().equals(value.fileId()),
                UIMessageParts.file(file.fileId(), file.url(), file.title(), file.mediaType(),
                    file.data(), file.providerMetadata()));
            return true;
        }

        private boolean replaceToolCall(ToolCallChunk tool) {
            replace(part -> part instanceof ToolCallPart value
                    && tool.toolCallId().equals(value.toolCallId()),
                UIMessageParts.toolCall(tool.toolCallId(), tool.toolName(), tool.input(),
                    tool.providerMetadata()));
            return true;
        }

        private boolean replaceToolResult(ToolResultChunk tool) {
            replace(part -> part instanceof ToolResultPart value
                    && tool.toolCallId().equals(value.toolCallId()),
                UIMessageParts.toolResult(tool.toolCallId(), tool.toolName(), tool.result(),
                    tool.providerMetadata()));
            return true;
        }

        private boolean replaceToolError(ToolErrorChunk tool) {
            replace(part -> part instanceof ToolErrorPart value
                    && tool.toolCallId().equals(value.toolCallId()),
                UIMessageParts.toolError(tool.toolCallId(), tool.toolName(), tool.errorText(),
                    tool.providerMetadata()));
            return true;
        }

        private boolean replaceToolApprovalRequest(ToolApprovalRequestChunk request) {
            replace(part -> part instanceof ToolApprovalRequestPart value
                    && approvalKey(request).equals(approvalKey(value)),
                UIMessageParts.toolApprovalRequest(request.approvalId(), request.toolCallId(),
                    request.toolName(), request.input(), request.stepIndex(),
                    request.providerMetadata()));
            return true;
        }

        private void replace(java.util.function.Predicate<UIMessagePart> predicate,
            UIMessagePart replacement) {
            for (var i = 0; i < parts.size(); i++) {
                if (predicate.test(parts.get(i))) {
                    parts.set(i, replacement);
                    return;
                }
            }
            parts.add(replacement);
        }

        private int indexOfText(String id) {
            for (var i = 0; i < parts.size(); i++) {
                if (parts.get(i) instanceof TextPart part && id.equals(part.id())) {
                    return i;
                }
            }
            return -1;
        }

        private int indexOfReasoning(String id) {
            for (var i = 0; i < parts.size(); i++) {
                if (parts.get(i) instanceof ReasoningPart part && id.equals(part.id())) {
                    return i;
                }
            }
            return -1;
        }

        private static String approvalKey(ToolApprovalRequestChunk request) {
            return request.approvalId() != null ? request.approvalId() : request.toolCallId();
        }

        private static String approvalKey(ToolApprovalRequestPart request) {
            return request.approvalId() != null ? request.approvalId() : request.toolCallId();
        }
    }
}
