package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Provider-neutral context passed to a server-side tool executor.
 *
 * <p>The context includes the parsed input and enough call metadata to correlate tool execution
 * with stream events and persisted conversation history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {
    /**
     * Provider or Halo generated tool call id.
     */
    private String toolCallId;
    /**
     * Name of the tool being executed.
     */
    private String toolName;
    /**
     * Parsed JSON arguments produced by the model.
     */
    private Map<String, Object> input;
    /**
     * Zero-based model invocation step that produced the tool call.
     */
    private Integer stepIndex;
    /**
     * Provider-neutral messages sent to the model for the step that produced this tool call.
     */
    private List<ModelMessage> messages;
    /**
     * Provider metadata from the tool call and surrounding step.
     */
    private Map<String, Object> providerMetadata;
}
