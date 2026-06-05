package run.halo.aifoundation.ui;

import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.LanguageModelUsage;

/**
 * Terminal chunk emitted when generation finishes normally.
 *
 * <p>Finish chunks update terminal state and may merge message metadata, but they do not become
 * {@link UIMessagePart} values.
 *
 * @param finishReason normalized finish reason
 * @param rawFinishReason provider-native finish reason when available
 * @param usage token usage for the stream
 * @param messageMetadata message-level metadata update to merge before finish aggregation
 */
public record FinishChunk(FinishReason finishReason, String rawFinishReason,
                          LanguageModelUsage usage, Object messageMetadata)
    implements UIMessageChunk {
    /**
     * Create a finish chunk without message metadata.
     *
     * @param finishReason normalized finish reason
     * @param rawFinishReason provider-native finish reason when available
     * @param usage token usage for the stream
     */
    public FinishChunk(FinishReason finishReason, String rawFinishReason,
        LanguageModelUsage usage) {
        this(finishReason, rawFinishReason, usage, null);
    }

    @Override
    public String type() {
        return UIMessageChunkType.FINISH;
    }
}
