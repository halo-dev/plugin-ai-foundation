package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Canonical tool lifecycle chunk carrying invalid or unrepairable tool input.
 *
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param errorText safe input error text
 * @param providerMetadata provider-specific metadata
 */
public record ToolInputErrorChunk(String toolCallId, String toolName, String errorText,
                                  Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolInputErrorChunk {
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_INPUT_ERROR;
    }
}
