package run.halo.aifoundation.chat;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.source.SourceReference;
import run.halo.aifoundation.source.SourceReferences;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

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
     * Display-safe source references used or returned during generation. When unset, this is derived
     * from source content parts.
     */
    private List<SourceReference> sources;
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
     * Provider-neutral messages produced by this generation call. Callers can append these after
     * their stored request messages before a later generation call.
     */
    private List<ModelMessage> responseMessages;
    /**
     * Aggregated tool calls across all steps.
     */
    private List<ToolCall> toolCalls;
    /**
     * Aggregated pending tool approval requests across all steps.
     */
    private List<ToolApprovalRequest> toolApprovalRequests;
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

    public List<SourceReference> getSources() {
        return sources != null ? sources : SourceReferences.fromContent(content);
    }
}
