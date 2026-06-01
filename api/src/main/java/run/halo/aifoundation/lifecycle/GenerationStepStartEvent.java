package run.halo.aifoundation.lifecycle;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationStep;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

/**
 * Lifecycle event emitted immediately before a provider generation step starts.
 *
 * <p>The event exposes the effective messages, tools, tool choice, stop condition, timeouts, and
 * provider options for that step. It is intended for observability and debugging, not mutation.
 */
@Value
@Builder
public class GenerationStepStartEvent {
    /**
     * Zero-based generation step index.
     */
    int stepIndex;
    /**
     * Original generation request.
     */
    GenerateTextRequest request;
    /**
     * Effective messages sent to the provider for this step.
     */
    List<ModelMessage> messages;
    /**
     * Steps completed before this step starts.
     */
    List<GenerationStep> previousSteps;
    /**
     * Effective tool definitions available to this step.
     */
    List<ToolDefinition> tools;
    /**
     * Effective tool choice for this step.
     */
    ToolChoice toolChoice;
    /**
     * Effective stop condition for the generation loop.
     */
    StopCondition stopWhen;
    /**
     * Provider-specific options grouped by provider namespace.
     */
    Map<String, Map<String, Object>> providerOptions;
    /**
     * Effective timeout settings.
     */
    GenerationTimeouts timeouts;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
