package run.halo.aifoundation.lifecycle;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Lifecycle event emitted before a server-side tool executor is invoked.
 *
 * <p>The input map has already been parsed from the model's tool-call arguments. Provider metadata
 * preserves provider-specific call details for logs or trace correlation.
 */
@Value
@Builder
public class GenerationToolCallStartEvent {
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
     * Parsed tool input arguments.
     */
    Map<String, Object> input;
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
