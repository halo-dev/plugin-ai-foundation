package run.halo.aifoundation.lifecycle;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

/**
 * Lifecycle event emitted after a server-side tool executor returns or fails.
 *
 * <p>Exactly one of {@link #getResult()} or {@link #getError()} is usually present. Duration is
 * measured by AI Foundation around the executor call and is useful for tracing slow tools.
 */
@Value
@Builder
public class GenerationToolCallFinishEvent {
    /**
     * Zero-based generation step index.
     */
    int stepIndex;
    /**
     * Provider-neutral tool call id.
     */
    String toolCallId;
    /**
     * Tool name requested by the model.
     */
    String toolName;
    /**
     * Tool result when execution succeeded.
     */
    ToolResult result;
    /**
     * Tool error when execution failed.
     */
    ToolError error;
    /**
     * Time spent executing the tool.
     */
    Duration duration;
    /**
     * Provider-specific metadata associated with the tool call.
     */
    Map<String, Object> providerMetadata;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
