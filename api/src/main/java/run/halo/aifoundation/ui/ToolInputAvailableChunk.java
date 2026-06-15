package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Canonical tool lifecycle chunk indicating that complete tool input is available.
 *
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param input parsed tool input
 * @param providerMetadata provider-specific metadata
 */
public record ToolInputAvailableChunk(String toolCallId, String toolName, Object input,
                                      Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolInputAvailableChunk {
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_INPUT_AVAILABLE;
    }
}
