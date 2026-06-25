package run.halo.aifoundation.ui;

import java.util.Map;
import org.reactivestreams.Publisher;
import run.halo.aifoundation.media.GeneratedFile;

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

    /**
     * Writes a file chunk that will be persisted as a {@link FilePart}.
     *
     * @param fileId stable file id used as the replacement key
     * @param url optional file URL
     * @param title optional display title
     * @param mediaType optional media type
     * @param data optional inline file data, commonly base64
     * @param providerMetadata provider-specific metadata
     */
    default void writeFile(String fileId, String url, String title, String mediaType, Object data,
        Map<String, Object> providerMetadata) {
        write(UIMessageChunks.file(fileId, url, title, mediaType, data, providerMetadata));
    }

    /**
     * Writes a generated file as a UI message file chunk.
     *
     * <p>The caller supplies the stable {@code fileId}; AI Foundation does not infer storage
     * identity or persist the file as an attachment.
     *
     * @param fileId stable file id used as the replacement key
     * @param file generated file returned by a model
     */
    default void writeFile(String fileId, GeneratedFile file) {
        writeFile(fileId, file, file == null ? null : file.getMetadata());
    }

    /**
     * Writes a generated file as a UI message file chunk with explicit metadata.
     *
     * @param fileId stable file id used as the replacement key
     * @param file generated file returned by a model
     * @param providerMetadata provider metadata to attach to the UI file part
     */
    default void writeFile(String fileId, GeneratedFile file,
        Map<String, Object> providerMetadata) {
        if (file == null) {
            throw new IllegalArgumentException("generated file must not be null");
        }
        var title = file.getTitle() != null ? file.getTitle() : file.getFilename();
        writeFile(fileId, file.getUrl(), title, file.getMediaType(), file.getBase64(),
            providerMetadata);
    }
}
