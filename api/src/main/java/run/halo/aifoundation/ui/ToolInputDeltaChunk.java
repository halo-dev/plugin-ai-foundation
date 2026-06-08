package run.halo.aifoundation.ui;

/**
 * Stream chunk carrying partial tool input.
 *
 * <p>Tool input chunks are progress events and are not persisted into {@link UIMessage#parts()}.
 *
 * @param id stable streamed input block identifier
 * @param toolCallId tool call identifier
 * @param toolName tool name
 * @param delta input fragment
 */
public record ToolInputDeltaChunk(String id, String toolCallId, String toolName, String delta)
    implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_INPUT_DELTA;
    }
}
