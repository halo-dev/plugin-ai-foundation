package run.halo.aifoundation.chat;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.tool.ToolChoice;

/**
 * Step-scoped overrides returned by {@link PrepareStepCallback}.
 *
 * <p>Only non-null fields override the current request. Overrides apply to the current model
 * invocation only and do not mutate the original {@link GenerateTextRequest}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreparedStep {
    /**
     * Replacement messages for the current model invocation.
     */
    private List<ModelMessage> messages;
    /**
     * Tool selection behavior for the current step.
     */
    private ToolChoice toolChoice;
    /**
     * Names of request tools that are active for the current step.
     */
    private List<String> activeTools;
    /**
     * Provider-specific options for the current step.
     */
    private Map<String, Map<String, Object>> providerOptions;
    /**
     * Maximum output tokens for the current step.
     */
    private Integer maxOutputTokens;
    /**
     * Sampling temperature for the current step.
     */
    private Double temperature;
    /**
     * Nucleus sampling value for the current step.
     */
    private Double topP;
    /**
     * Top-k sampling value for the current step.
     */
    private Integer topK;
    /**
     * Presence penalty for the current step.
     */
    private Double presencePenalty;
    /**
     * Frequency penalty for the current step.
     */
    private Double frequencyPenalty;
    /**
     * Stop sequences for the current step.
     */
    private List<String> stopSequences;
    /**
     * Deterministic sampling seed for the current step.
     */
    private Integer seed;
    /**
     * Retry attempts for retryable non-streaming provider calls in the current step.
     */
    private Integer maxRetries;
    /**
     * Stop condition override to use after this step completes.
     */
    private transient StopCondition stopWhen;

    public static PreparedStep empty() {
        return new PreparedStep();
    }
}
