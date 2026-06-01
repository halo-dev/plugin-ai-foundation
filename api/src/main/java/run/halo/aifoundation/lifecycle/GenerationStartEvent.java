package run.halo.aifoundation.lifecycle;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationRequestMetadata;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

/**
 * Lifecycle event emitted before text generation begins.
 */
@Value
@Builder
public class GenerationStartEvent {
    /**
     * Original generation request.
     */
    GenerateTextRequest request;
    /**
     * Request metadata created for this generation.
     */
    GenerationRequestMetadata requestMetadata;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
    /**
     * Effective tool definitions available to the first step.
     */
    List<ToolDefinition> tools;
    /**
     * Effective tool choice for the first step.
     */
    ToolChoice toolChoice;
    /**
     * Effective stop condition for the generation loop.
     */
    StopCondition stopWhen;
    /**
     * Effective timeout settings.
     */
    GenerationTimeouts timeouts;
}
