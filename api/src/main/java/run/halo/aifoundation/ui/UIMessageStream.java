package run.halo.aifoundation.ui;

import java.util.Objects;
import reactor.core.publisher.Flux;

/**
 * Structured stream of frontend-facing UI message chunks.
 *
 * <p>The stream is framework-neutral. Callers can expose {@link #chunks()} directly,
 * encode it as server-sent events with {@link UIMessageStreamResponse}, or read it
 * into message snapshots with {@link UIMessageStreamReader}.
 */
public final class UIMessageStream {

    private final Flux<UIMessageChunk> chunks;

    /**
     * Creates a UI message stream from structured chunks.
     *
     * @param chunks chunk publisher
     */
    public UIMessageStream(Flux<UIMessageChunk> chunks) {
        this.chunks = Objects.requireNonNull(chunks, "chunks must not be null");
    }

    /**
     * Returns the structured chunks.
     *
     * @return chunk stream
     */
    public Flux<UIMessageChunk> chunks() {
        return chunks;
    }
}
