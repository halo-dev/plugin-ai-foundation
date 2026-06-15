package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted dynamic tool part whose type is {@code tool-${toolName}}.
 *
 * @param type dynamic tool part type
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param state current tool lifecycle state
 * @param input tool input when available
 * @param inputText streamed input text while input is incomplete
 * @param output tool output when available
 * @param errorText safe error text for failed tools
 * @param approval optional approval metadata
 * @param providerMetadata provider-specific metadata
 */
public record ToolPart(String type, String toolCallId, String toolName, ToolPartState state,
                       Object input, String inputText, Object output, String errorText,
                       ToolApproval approval, Map<String, Object> providerMetadata)
    implements UIMessagePart {

    public ToolPart {
        UIMessageDynamicNames.requireToolType(type, toolName);
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    /**
     * Creates a dynamic tool part type for the given tool name.
     *
     * @param toolName tool name
     * @return dynamic type
     */
    public static String typeFor(String toolName) {
        return UIMessageDynamicNames.toolType(toolName);
    }
}
