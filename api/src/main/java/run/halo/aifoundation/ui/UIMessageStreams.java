package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import run.halo.aifoundation.exception.AiGenerationCancelledException;

/**
 * Entry points for creating Halo UI message streams.
 *
 * <p>Example:
 * <pre>{@code
 * UIMessageStream stream = UIMessageStreams.createWithOptions(options -> options
 *     .messageId("msg_1")
 *     .onFinish(finish -> save(finish.messages()))
 *     .execute(writer -> {
 *         writer.writeText("Hello");
 *         writer.writeData("notice", Map.of("level", "info"));
 *     }));
 * }</pre>
 */
public final class UIMessageStreams {

    private UIMessageStreams() {
    }

    /**
     * Creates a stream by executing a writer callback.
     *
     * @param execute callback that writes chunks or merges other chunk streams
     * @return UI message stream
     */
    public static UIMessageStream create(Consumer<UIMessageStreamWriter> execute) {
        return UIMessageStreams.<Void>createWithOptions(options -> options.execute(execute));
    }

    /**
     * Creates a stream with a fixed assistant message id.
     *
     * @param messageId assistant message id used by the reader and finish callback
     * @param execute callback that writes chunks or merges other chunk streams
     * @return UI message stream
     */
    public static UIMessageStream create(String messageId,
        Consumer<UIMessageStreamWriter> execute) {
        return UIMessageStreams.<Void>createWithOptions(options -> options.messageId(messageId)
            .execute(execute));
    }

    /**
     * Creates a stream with full lifecycle options.
     *
     * <p>The callback is invoked when the stream is subscribed. Writer errors are
     * mapped to error or abort chunks, and {@link UIMessageStreamOptions#onFinish(Consumer)}
     * receives the aggregated response message after the stream reaches its terminal state.
     *
     * @param configure option customizer
     * @param <M> message metadata type
     * @return UI message stream
     */
    public static <M> UIMessageStream createWithOptions(
        Consumer<UIMessageStreamOptions<M>> configure) {
        Objects.requireNonNull(configure, "configure must not be null");
        return new UIMessageStream(Flux.defer(() -> {
            var options = new UIMessageStreamOptions<M>();
            configure.accept(options);
            var writer = new DefaultWriter(options);
            try {
                options.execute().accept(writer);
            } catch (Throwable error) {
                writer.write(writer.errorChunk(error));
            }
            var chunks = writer.toFlux().cache();
            var reader = UIMessageStreamReader.<M>read(readerOptions -> readerOptions
                .stream(new UIMessageStream(chunks))
                .message(options.message())
                .originalMessages(options.originalMessages())
                .messageIdGenerator(options.messageIdGenerator())
                .metadataSupplier(options.metadataSupplier())
                .metadataMerger(options.metadataMerger())
                .errorHandler(options.errorHandler())
                .onError(options.readErrorHandler())
                .terminateOnError(options.terminateOnError()));
            reader.responseMessage()
                .zipWith(reader.finish())
                .map(tuple -> toFinish(options.originalMessages(), tuple.getT1(), tuple.getT2()))
                .subscribe(options.finishHandler());
            return chunks;
        }));
    }

    private static final class DefaultWriter implements UIMessageStreamWriter {
        private final UIMessageStreamOptions<?> options;
        private final List<Flux<UIMessageChunk>> segments = new ArrayList<>();
        private final AtomicBoolean terminalEmitted = new AtomicBoolean();
        private int textIdSequence;

        DefaultWriter(UIMessageStreamOptions<?> options) {
            this.options = options;
        }

        @Override
        public void write(UIMessageChunk chunk) {
            segments.add(Flux.just(Objects.requireNonNull(chunk, "chunk must not be null"))
                .handle(this::emitIfAllowed));
        }

        @Override
        public void merge(Publisher<? extends UIMessageChunk> stream) {
            Objects.requireNonNull(stream, "stream must not be null");
            segments.add(Flux.from(stream)
                .cast(UIMessageChunk.class)
                .onErrorResume(error -> Flux.just(errorChunk(error)))
                .handle(this::emitIfAllowed));
        }

        @Override
        public void writeText(String text) {
            writeText(nextTextId(), text);
        }

        @Override
        public void writeText(String id, String text) {
            write(UIMessageChunks.textStart(id));
            write(UIMessageChunks.textDelta(id, text));
            write(UIMessageChunks.textEnd(id));
        }

        Flux<UIMessageChunk> toFlux() {
            return Flux.concat(segments);
        }

        private UIMessageChunk errorChunk(Throwable error) {
            if (isCancellation(error)) {
                return UIMessageChunks.abort();
            }
            return UIMessageChunks.error(options.errorHandler().apply(error));
        }

        private boolean isCancellation(Throwable error) {
            if (options.cancellationToken() != null
                && options.cancellationToken().isCancellationRequested()) {
                return true;
            }
            var current = error;
            while (current != null) {
                if (current instanceof AiGenerationCancelledException) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }

        private void emitIfAllowed(UIMessageChunk chunk, SynchronousSink<UIMessageChunk> sink) {
            if (!isTerminal(chunk)) {
                if (!terminalEmitted.get()) {
                    sink.next(chunk);
                }
                return;
            }
            if (terminalEmitted.compareAndSet(false, true)) {
                sink.next(chunk);
            }
        }

        private boolean isTerminal(UIMessageChunk chunk) {
            return UIMessageChunkType.FINISH.equals(chunk.type())
                || UIMessageChunkType.ERROR.equals(chunk.type())
                || UIMessageChunkType.ABORT.equals(chunk.type());
        }

        private String nextTextId() {
            var prefix = options.messageId() == null || options.messageId().isBlank()
                ? "text"
                : options.messageId() + "-text";
            return prefix + "-" + (++textIdSequence);
        }

    }

    private static <M> UIMessageStreamFinish<M> toFinish(List<UIMessage<M>> originalMessages,
        UIMessage<M> responseMessage, UIMessageStreamTerminal terminal) {
        var updated = new ArrayList<>(originalMessages);
        var continuation = !updated.isEmpty()
            && updated.getLast().role() == UIMessageRole.ASSISTANT
            && updated.getLast().id().equals(responseMessage.id());
        if (continuation) {
            updated.set(updated.size() - 1, responseMessage);
        } else {
            updated.add(responseMessage);
        }
        return new UIMessageStreamFinish<>(updated, responseMessage, continuation, terminal);
    }
}
