package run.halo.aifoundation.ui;

/**
 * Stream chunk carrying message-level metadata updates.
 *
 * <p>Message metadata chunks update {@link UIMessage#metadata()} and do not become message parts.
 *
 * @param messageMetadata metadata update to merge into the response message
 */
public record MessageMetadataChunk(Object messageMetadata) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.MESSAGE_METADATA;
    }
}
