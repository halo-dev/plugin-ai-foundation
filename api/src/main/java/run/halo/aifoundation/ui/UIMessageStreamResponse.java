package run.halo.aifoundation.ui;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * HTTP-friendly descriptor for returning Halo UI message streams.
 *
 * <p>The descriptor adds a protocol header automatically and can encode chunks as
 * server-sent event frames when a serializer is supplied.
 *
 * <pre>{@code
 * UIMessageStreamResponse response = new UIMessageStreamResponse(stream, json::writeValueAsString);
 * return ServerResponse.ok()
 *     .headers(headers -> response.headers().forEach(headers::add))
 *     .body(response.body(), String.class);
 * }</pre>
 */
public final class UIMessageStreamResponse {

    /** Header that marks the response as a Halo UI message stream. */
    public static final String PROTOCOL_HEADER = "X-Halo-AI-UI-Message-Stream";
    /** Current Halo UI message stream protocol version. */
    public static final String PROTOCOL_VERSION = "v1";
    /** Final server-sent event marker appended by {@link #body()}. */
    public static final String DONE_MARKER = "[DONE]";

    private final UIMessageStream stream;
    private final Function<UIMessageChunk, String> serializer;

    /**
     * Creates a response descriptor without a body serializer.
     *
     * @param stream structured UI message stream
     */
    public UIMessageStreamResponse(UIMessageStream stream) {
        this(stream, null);
    }

    /**
     * Creates a response descriptor with a body serializer.
     *
     * @param stream structured UI message stream
     * @param serializer chunk serializer used by {@link #body()}
     */
    public UIMessageStreamResponse(UIMessageStream stream,
        Function<UIMessageChunk, String> serializer) {
        this.stream = Objects.requireNonNull(stream, "stream must not be null");
        this.serializer = serializer;
    }

    /**
     * Returns protocol headers that callers should add to their HTTP response.
     *
     * @return immutable response headers
     */
    public Map<String, String> headers() {
        return Map.of(PROTOCOL_HEADER, PROTOCOL_VERSION);
    }

    /**
     * Returns the structured stream without encoding it.
     *
     * @return UI message chunk stream
     */
    public Flux<UIMessageChunk> stream() {
        return stream.chunks();
    }

    /**
     * Encodes chunks as server-sent event data frames and appends {@link #DONE_MARKER}.
     *
     * @return server-sent event frame body
     * @throws IllegalStateException when the response was created without a serializer
     */
    public Flux<String> body() {
        if (serializer == null) {
            return Flux.error(new IllegalStateException(
                "UI message stream response body requires a chunk serializer"));
        }
        return stream()
            .map(serializer)
            .map(UIMessageStreamResponse::sseFrame)
            .concatWithValues(sseFrame(DONE_MARKER));
    }

    private static String sseFrame(String data) {
        return "data: " + data + "\n\n";
    }
}
