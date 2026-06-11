package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Dynamic tool stream chunk whose type is {@code tool-${toolName}}.
 *
 * @param type dynamic tool chunk type
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param state current tool lifecycle state
 * @param input tool input when available
 * @param inputTextDelta streamed input text delta while input is incomplete
 * @param output tool output when available
 * @param errorText safe error text for failed or denied tools
 * @param approval optional approval metadata
 * @param providerMetadata provider-specific metadata
 */
public record ToolChunk(String type, String toolCallId, String toolName, ToolPartState state,
                        Object input, String inputTextDelta, Object output, String errorText,
                        ToolApproval approval, Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolChunk {
        UIMessageDynamicNames.requireToolType(type, toolName);
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    /**
     * Creates a dynamic tool chunk type for the given tool name.
     *
     * @param toolName tool name
     * @return dynamic type
     */
    public static String typeFor(String toolName) {
        return UIMessageDynamicNames.toolType(toolName);
    }
}
