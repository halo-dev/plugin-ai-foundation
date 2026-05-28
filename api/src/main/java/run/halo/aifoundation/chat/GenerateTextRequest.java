package run.halo.aifoundation.chat;

import java.beans.Transient;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.options.ProviderOptions;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

/**
 * Provider-neutral request for text generation.
 *
 * <p>Use either {@link #prompt} for a single user message or {@link #messages} for a conversation,
 * but not both. {@link #system} is a top-level system instruction and can be used with either
 * input style.
 *
 * <pre>{@code
 * GenerateTextRequest request = GenerateTextRequest.builder()
 *     .system("You are concise.")
 *     .messages(List.of(
 *         ModelMessage.user("What is Halo CMS?")
 *     ))
 *     .temperature(0.7)
 *     .maxOutputTokens(1024)
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTextRequest {
    /**
     * Optional system instruction applied before prompt or messages.
     */
    private String system;
    /**
     * Single-turn user prompt. Mutually exclusive with {@link #messages}.
     */
    private String prompt;
    /**
     * Conversation messages. Mutually exclusive with {@link #prompt}.
     */
    private List<ModelMessage> messages;
    /**
     * Maximum number of output tokens requested from the provider.
     */
    private Integer maxOutputTokens;
    /**
     * Sampling temperature.
     */
    private Double temperature;
    /**
     * Nucleus sampling value.
     */
    private Double topP;
    /**
     * Top-k sampling value.
     */
    private Integer topK;
    /**
     * Presence penalty when supported by the provider.
     */
    private Double presencePenalty;
    /**
     * Frequency penalty when supported by the provider.
     */
    private Double frequencyPenalty;
    /**
     * Stop sequences. Generation should stop when any sequence is produced.
     */
    private List<String> stopSequences;
    /**
     * Provider-specific options grouped by provider namespace, for example
     * {@code Map.of("openai", Map.of("seed", 42))}.
     */
    private Map<String, Map<String, Object>> providerOptions;
    /**
     * Request-scoped HTTP headers sent to providers when the selected provider adapter supports
     * dynamic request headers.
     */
    private Map<String, String> headers;
    /**
     * Caller metadata exposed to lifecycle callbacks. This data is not added to model prompts.
     */
    private Map<String, Object> metadata;
    /**
     * Caller context exposed to lifecycle callbacks. This data is not added to model prompts.
     */
    private Map<String, Object> context;
    /**
     * Optional structured output specification.
     */
    private OutputSpec output;
    /**
     * Request-scoped tools that the model may call.
     */
    private List<ToolDefinition> tools;
    /**
     * Tool selection behavior for this request.
     */
    private ToolChoice toolChoice;
    /**
     * Step continuation rule. If omitted, generation runs a single model step.
     */
    private transient StopCondition stopWhen;
    /**
     * Optional callback that can override messages, tool choice, active tools, and settings before
     * each model step.
     */
    private transient PrepareStepCallback prepareStep;
    /**
     * Request-scoped lifecycle observer. Callback failures are reported as generation warnings.
     */
    private transient GenerationLifecycle lifecycle;
    /**
     * Request-scoped cancellation signal.
     */
    private transient CancellationToken cancellationToken;
    /**
     * Request-scoped timeout controls for total generation, provider steps, and tools.
     */
    private transient GenerationTimeouts timeouts;

    @Transient
    public StopCondition getStopWhen() {
        return stopWhen;
    }

    @Transient
    public PrepareStepCallback getPrepareStep() {
        return prepareStep;
    }

    @Transient
    public GenerationLifecycle getLifecycle() {
        return lifecycle;
    }

    @Transient
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    @Transient
    public GenerationTimeouts getTimeouts() {
        return timeouts;
    }

    public static class GenerateTextRequestBuilder {
        public GenerateTextRequestBuilder providerOptions(
            Map<String, Map<String, Object>> providerOptions) {
            this.providerOptions = providerOptions;
            return this;
        }

        public GenerateTextRequestBuilder providerOptions(ProviderOptions.NamespaceOptions... options) {
            this.providerOptions = ProviderOptions.of(options);
            return this;
        }
    }
}
