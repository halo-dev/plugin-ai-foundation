package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted assistant tool call part.
 *
 * @param toolCallId stable tool call identifier
 * @param toolName tool name
 * @param input tool input
 * @param providerMetadata provider-specific metadata
 */
public record ToolCallPart(String toolCallId, String toolName, Map<String, Object> input,
                           Map<String, Object> providerMetadata) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_CALL;
    }
}
