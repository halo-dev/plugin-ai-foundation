package run.halo.aifoundation;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A tool call requested by the model.
 *
 * <p>Tool calls appear in {@link GenerationStep#getToolCalls()}, {@link GenerationContentPart}
 * with {@link PartType#TOOL_CALL}, and {@link TextStreamPart} with {@link PartType#TOOL_CALL}.
 * Halo executes a matching {@link ToolDefinition#getExecutor()} when one is available and the
 * request has remaining {@link GenerateTextRequest#getMaxSteps()} budget.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    /**
     * Provider or Halo generated call id. Tool results and errors use the same id.
     */
    private String toolCallId;
    /**
     * Requested tool name.
     */
    private String toolName;
    /**
     * Parsed arguments for the tool executor.
     */
    private Map<String, Object> input;
    /**
     * Original provider arguments before normalization. This is useful for diagnostics when
     * parsing fails or a provider uses a non-standard format.
     */
    private Object rawInput;
    /**
     * Provider-specific metadata associated with the call.
     */
    private Map<String, Object> providerMetadata;
}
