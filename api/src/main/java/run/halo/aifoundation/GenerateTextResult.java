package run.halo.aifoundation;

import java.util.Map;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Final accumulated result for {@link LanguageModel#generateText(GenerateTextRequest)} and
 * {@link StreamTextResult#result()}.
 *
 * <p>{@link #text} is the authoritative final assistant text. When an {@link OutputSpec} is used,
 * {@link #outputText} stores the JSON text that was parsed and {@link #output} stores the parsed
 * and locally validated value. Rich traces such as reasoning, tool calls, warnings, and provider
 * metadata are exposed without requiring callers to depend on Spring AI types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTextResult {
    /**
     * Final assistant text.
     */
    private String text;
    /**
     * Parsed structured output when requested.
     */
    private Object output;
    /**
     * Raw text fragment used to parse structured output.
     */
    private String outputText;
    /**
     * Last-step visible reasoning text when the provider returns reasoning.
     */
    private String reasoningText;
    /**
     * Normalized generation content parts.
     */
    private List<GenerationContentPart> content;
    /**
     * Last-step reasoning parts, including provider metadata needed for continuation.
     */
    private List<ReasoningPart> reasoning;
    /**
     * Normalized finish reason.
     */
    private FinishReason finishReason;
    /**
     * Provider finish reason before normalization.
     */
    private String rawFinishReason;
    /**
     * Usage for the last model step.
     */
    private LanguageModelUsage usage;
    /**
     * Aggregate usage across all model steps.
     */
    private LanguageModelUsage totalUsage;
    /**
     * Non-fatal diagnostics emitted while serving the request.
     */
    private List<GenerationWarning> warnings;
    /**
     * Request-side metadata for the final step.
     */
    private GenerationRequestMetadata request;
    /**
     * Response-side metadata for the final step.
     */
    private GenerationResponseMetadata response;
    /**
     * Per-step generation trace.
     */
    private List<GenerationStep> steps;
    /**
     * Aggregated tool calls across all steps.
     */
    private List<ToolCall> toolCalls;
    /**
     * Aggregated successful tool results across all steps.
     */
    private List<ToolResult> toolResults;
    /**
     * Aggregated tool execution errors across all steps.
     */
    private List<ToolError> toolErrors;
    /**
     * Sanitized provider metadata.
     */
    private Map<String, Object> providerMetadata;
}
