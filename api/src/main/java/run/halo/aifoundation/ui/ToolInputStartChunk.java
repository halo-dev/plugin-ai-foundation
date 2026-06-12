package run.halo.aifoundation.ui;

/**
 * Canonical tool input lifecycle chunk indicating that tool input streaming has started.
 *
 * @param toolCallId stable tool call id
 * @param toolName tool name
 */
public record ToolInputStartChunk(String toolCallId, String toolName) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_INPUT_START;
    }
}
