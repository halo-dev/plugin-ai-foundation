package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Stream chunk describing an assistant tool call.
 *
 * <p>Tool call chunks are aggregated into {@link ToolCallPart} values using {@code toolCallId} as
 * the stable replacement key.
 *
 * @param toolCallId stable tool call identifier
 * @param toolName tool name
 * @param input validated or provider-produced tool input
 * @param providerMetadata provider-specific metadata
 */
public record ToolCallChunk(String toolCallId, String toolName, Map<String, Object> input,
                            Map<String, Object> providerMetadata) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_CALL;
    }
}
