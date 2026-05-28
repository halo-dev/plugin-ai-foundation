package run.halo.aifoundation.tool;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a failed server-side tool execution.
 *
 * <p>Tool errors are non-transport errors: the text generation request itself may still complete,
 * but the current step records that a requested tool could not be executed successfully.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolError {
    /**
     * Id of the tool call this error answers.
     */
    private String toolCallId;
    /**
     * Name of the requested tool.
     */
    private String toolName;
    /**
     * Human-readable error text safe to expose to the caller.
     */
    private String errorText;
    /**
     * Provider or Halo metadata associated with the error.
     */
    private Map<String, Object> providerMetadata;
}
