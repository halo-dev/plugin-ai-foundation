package run.halo.aifoundation.ui;

/**
 * Stream chunk that starts streamed tool input.
 *
 * <p>Tool input chunks are progress events and are not persisted into {@link UIMessage#parts()}.
 *
 * @param id stable streamed input block identifier
 * @param toolCallId tool call identifier
 * @param toolName tool name
 */
public record ToolInputStartChunk(String id, String toolCallId, String toolName)
    implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_INPUT_START;
    }
}
