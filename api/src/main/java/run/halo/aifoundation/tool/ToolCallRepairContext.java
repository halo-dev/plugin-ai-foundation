package run.halo.aifoundation.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Provider-neutral context passed to a tool call repair callback.
 */
@Data
@NoArgsConstructor
public class ToolCallRepairContext {
    /**
     * Typed reason the tool call entered recovery.
     */
    private ToolCallFailureKind failureKind;
    /**
     * Original invalid tool call produced by the model.
     */
    private ToolCall toolCall;
    /**
     * Matching request-scoped tool definition.
     */
    private ToolDefinition tool;
    /**
     * Complete request-scoped tool set currently available to the model.
     */
    private List<ToolDefinition> availableTools;
    /**
     * Human-readable validation error from the original input validation failure.
     */
    private String validationError;
    /**
     * JSON-path-like location that was validated.
     */
    private String validationPath;
    /**
     * Zero-based model invocation step that produced the tool call.
     */
    private Integer stepIndex;
    /**
     * Provider-neutral messages sent to the model for the step that produced this tool call.
     */
    private List<ModelMessage> messages;
    /**
     * Caller request context. This data is not added to model prompts.
     */
    private Map<String, Object> requestContext;
    /**
     * Provider metadata from the tool call and surrounding step.
     */
    private Map<String, Object> providerMetadata;

    @Builder
    private ToolCallRepairContext(ToolCallFailureKind failureKind, ToolCall toolCall,
        ToolDefinition tool, List<ToolDefinition> availableTools, String validationError,
        String validationPath, Integer stepIndex, List<ModelMessage> messages,
        Map<String, Object> requestContext, Map<String, Object> providerMetadata) {
        this.failureKind = failureKind;
        this.toolCall = toolCall;
        this.tool = tool;
        this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
        this.validationError = validationError;
        this.validationPath = validationPath;
        this.stepIndex = stepIndex;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.requestContext = immutableMap(requestContext);
        this.providerMetadata = immutableMap(providerMetadata);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
