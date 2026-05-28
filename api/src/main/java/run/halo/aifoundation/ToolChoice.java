package run.halo.aifoundation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Controls how the model may use request tools.
 *
 * <p>If omitted, providers use their default behavior, which is usually equivalent to
 * {@link Type#AUTO}. Some providers may not support every mode; unsupported details are reported
 * as validation errors or warnings depending on the adapter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolChoice {
    /**
     * Tool selection mode.
     */
    public enum Type {
        /**
         * Let the model decide whether to call a tool.
         */
        AUTO,
        /**
         * Disable tool calling even when tools are present.
         */
        NONE,
        /**
         * Require the model to call at least one available tool when the provider supports this
         * mode.
         */
        REQUIRED,
        /**
         * Force or bias the model toward one named tool when the provider supports this mode.
         */
        TOOL
    }

    /**
     * Selection mode.
     */
    private Type type;
    /**
     * Required when {@link #type} is {@link Type#TOOL}; ignored for other modes.
     */
    private String toolName;

    /**
     * Creates an automatic tool choice.
     */
    public static ToolChoice auto() {
        return ToolChoice.builder().type(Type.AUTO).build();
    }

    /**
     * Creates a choice that disables tool calling for this request.
     */
    public static ToolChoice none() {
        return ToolChoice.builder().type(Type.NONE).build();
    }

    /**
     * Creates a choice that requires the model to call one of the provided tools.
     */
    public static ToolChoice required() {
        return ToolChoice.builder().type(Type.REQUIRED).build();
    }

    /**
     * Creates a choice for a specific tool.
     */
    public static ToolChoice tool(String toolName) {
        return ToolChoice.builder().type(Type.TOOL).toolName(toolName).build();
    }
}
