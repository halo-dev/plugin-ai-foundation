package run.halo.aifoundation.ui;

import org.reactivestreams.Publisher;

/**
 * Writes chunks into a Halo UI message stream.
 */
public interface UIMessageStreamWriter {

    /**
     * Writes one structured chunk.
     *
     * @param chunk chunk to emit
     */
    void write(UIMessageChunk chunk);

    /**
     * Merges another chunk publisher into the current stream.
     *
     * @param stream chunk publisher to merge
     */
    void merge(Publisher<? extends UIMessageChunk> stream);

    /**
     * Merges another UI message stream into the current stream.
     *
     * @param stream UI message stream to merge
     */
    default void merge(UIMessageStream stream) {
        merge(stream.chunks());
    }

    /**
     * Writes persisted custom data.
     *
     * @param name data part name
     * @param data data payload
     */
    default void writeData(String name, Object data) {
        writeData(name, data, false);
    }

    /**
     * Writes custom data and controls whether it is persisted into the response message.
     *
     * @param name data part name
     * @param data data payload
     * @param transientData whether the data is stream-only and should not be persisted
     */
    default void writeData(String name, Object data, boolean transientData) {
        write(UIMessageChunks.data(name, data, transientData));
    }

    /**
     * Writes stream-only custom data.
     *
     * @param name data part name
     * @param data data payload
     */
    default void writeTransientData(String name, Object data) {
        writeData(name, data, true);
    }

    /**
     * Writes a message-level metadata update.
     *
     * @param messageMetadata metadata update object consumed by the configured merger
     */
    default void writeMessageMetadata(Object messageMetadata) {
        write(UIMessageChunks.messageMetadata(messageMetadata));
    }

    /**
     * Writes a complete text block with a generated text part id.
     *
     * @param text text content
     */
    void writeText(String text);

    /**
     * Writes a complete text block with an explicit text part id.
     *
     * @param id text part id
     * @param text text content
     */
    void writeText(String id, String text);
}
