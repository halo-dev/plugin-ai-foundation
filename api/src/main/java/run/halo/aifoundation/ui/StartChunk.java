package run.halo.aifoundation.ui;

/**
 * Stream chunk that starts or identifies an assistant response message.
 *
 * @param messageId response message id supplied by the stream
 * @param messageMetadata optional metadata update to merge into the response message
 */
public record StartChunk(String messageId, Object messageMetadata) implements UIMessageChunk {
    /**
     * Create a start chunk without message metadata.
     *
     * @param messageId response message id supplied by the stream
     */
    public StartChunk(String messageId) {
        this(messageId, null);
    }

    @Override
    public String type() {
        return UIMessageChunkType.START;
    }
}
