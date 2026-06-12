package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Canonical tool lifecycle chunk carrying successful tool output.
 *
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param output tool output
 * @param providerMetadata provider-specific metadata
 */
public record ToolOutputAvailableChunk(String toolCallId, String toolName, Object output,
                                       Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolOutputAvailableChunk {
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_OUTPUT_AVAILABLE;
    }
}
