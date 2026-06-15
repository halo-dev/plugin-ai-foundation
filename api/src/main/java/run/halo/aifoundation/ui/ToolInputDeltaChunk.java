package run.halo.aifoundation.ui;

/**
 * Canonical tool input lifecycle chunk carrying streamed tool input text.
 *
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param inputTextDelta incremental tool input text
 */
public record ToolInputDeltaChunk(String toolCallId, String toolName, String inputTextDelta)
    implements UIMessageChunk {

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_INPUT_DELTA;
    }
}
