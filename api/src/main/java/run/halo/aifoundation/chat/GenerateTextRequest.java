package run.halo.aifoundation.chat;

import java.beans.Transient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.options.ProviderOptions;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolCallRepairCallback;
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
     * Optional deterministic sampling seed. Providers and models may differ in how strictly they
     * honor this value.
     */
    private Integer seed;
    /**
     * Maximum retry attempts for retryable non-streaming provider calls. Set to {@code 0} to
     * disable retries for this request.
     */
    private Integer maxRetries;
    /**
     * Provider-specific options grouped by provider namespace, for example
     * {@code Map.of("openai", Map.of("response_format", "..."))}.
     *
     * <p>For reasoning behavior, prefer {@link #reasoning} unless a provider-native option is
     * intentionally needed. Explicit typed reasoning settings must not be combined with known
     * provider-native reasoning keys in this map.
     */
    private Map<String, Map<String, Object>> providerOptions;
    /**
     * Optional request-scoped reasoning behavior. When unset or set to
     * {@link ReasoningOptions#providerDefault()}, generation uses the selected provider and model
     * default behavior without adding generic provider-native reasoning parameters.
     */
    private ReasoningOptions reasoning;
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
     * Optional callback that can repair invalid input for known server-side tool calls before tool
     * execution.
     */
    private transient ToolCallRepairCallback toolCallRepair;
    /**
     * Request-scoped cancellation signal.
     */
    private transient CancellationToken cancellationToken;
    /**
     * Request-scoped timeout controls for total generation, provider steps, and tools.
     */
    private transient GenerationTimeouts timeouts;
    /**
     * Request-scoped language model middleware. These middleware entries run inside any model-level
     * middleware and preserve caller-provided list order.
     */
    private transient List<LanguageModelMiddleware> middleware;

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
    public ToolCallRepairCallback getToolCallRepair() {
        return toolCallRepair;
    }

    @Transient
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    @Transient
    public GenerationTimeouts getTimeouts() {
        return timeouts;
    }

    @Transient
    public List<LanguageModelMiddleware> getMiddleware() {
        return middleware;
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

        public GenerateTextRequestBuilder middleware(LanguageModelMiddleware... middleware) {
            this.middleware = middleware != null ? Arrays.asList(middleware) : null;
            return this;
        }
    }
}
