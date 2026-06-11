package run.halo.aifoundation.ui;

/**
 * One frontend-facing event in the Halo UI message stream protocol.
 */
public sealed interface UIMessageChunk permits StartChunk, TextStartChunk, TextDeltaChunk,
    TextEndChunk, ReasoningStartChunk, ReasoningDeltaChunk, ReasoningEndChunk, DataChunk,
    MessageMetadataChunk, SourceUrlChunk, FileChunk, ToolChunk, FinishStepChunk, FinishChunk,
    ErrorChunk, AbortChunk {

    /**
     * Stable protocol discriminator for this chunk.
     *
     * @return chunk protocol type
     */
    String type();

    /**
     * JavaBean accessor for serializers that do not inspect plain interface methods.
     *
     * @return chunk protocol type
     */
    default String getType() {
        return type();
    }
}
