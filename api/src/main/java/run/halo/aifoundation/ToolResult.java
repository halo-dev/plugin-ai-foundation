package run.halo.aifoundation;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a successful server-side tool execution.
 *
 * <p>A result is correlated with the model's original {@link ToolCall} through
 * {@link #toolCallId}. It can appear in generation content, step details, and stream events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    /**
     * Id of the tool call this result answers.
     */
    private String toolCallId;
    /**
     * Name of the executed tool.
     */
    private String toolName;
    /**
     * JSON-serializable result returned by the tool executor.
     */
    private Object result;
    /**
     * Provider or Halo metadata associated with the result.
     */
    private Map<String, Object> providerMetadata;
}
