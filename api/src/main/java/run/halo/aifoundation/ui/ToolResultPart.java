package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted final successful result for a tool call.
 *
 * @param toolCallId tool call identifier being answered
 * @param toolName tool name
 * @param result tool result payload
 * @param providerMetadata provider-specific metadata
 */
public record ToolResultPart(String toolCallId, String toolName, Object result,
                             Map<String, Object> providerMetadata) implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_RESULT;
    }
}
