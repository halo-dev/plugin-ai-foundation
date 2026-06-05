package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Stream chunk carrying the final failed result for a tool call.
 *
 * @param toolCallId tool call identifier being answered
 * @param toolName tool name
 * @param errorText safe error text
 * @param providerMetadata provider-specific metadata
 */
public record ToolErrorChunk(String toolCallId, String toolName, String errorText,
                             Map<String, Object> providerMetadata) implements UIMessageChunk {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_ERROR;
    }
}
