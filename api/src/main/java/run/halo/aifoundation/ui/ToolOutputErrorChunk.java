package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Canonical tool lifecycle chunk carrying a failed tool output.
 *
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param errorText safe error text
 * @param providerMetadata provider-specific metadata
 */
public record ToolOutputErrorChunk(String toolCallId, String toolName, String errorText,
                                   Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolOutputErrorChunk {
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_OUTPUT_ERROR;
    }
}
