package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Provider-neutral context passed to a tool call repair callback.
 *
 * <p>Inspect {@link #validationError} and {@link #validationPath} against the matching tool schema, then return a
 * repaired call or leave the failure unchanged. Preserve the original tool name and call identity. For example, a
 * callback can normalize a numeric string to the integer required by the input schema before returning
 * {@link ToolCallRepairResult#repaired(ToolCall)}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRepairContext {
    /** Original invalid tool call produced by the model. */
    private ToolCall toolCall;
    /** Matching request-scoped tool definition. */
    private ToolDefinition tool;
    /** Human-readable validation error from the original input validation failure. */
    private String validationError;
    /** JSON-path-like location that was validated. */
    private String validationPath;
    /** Zero-based model invocation step that produced the tool call. */
    private Integer stepIndex;
    /** Provider-neutral messages sent to the model for the step that produced this tool call. */
    private List<ModelMessage> messages;
    /** Caller request context. This data is not added to model prompts. */
    private Map<String, Object> requestContext;
    /** Provider metadata from the tool call and surrounding step. */
    private Map<String, Object> providerMetadata;
}
