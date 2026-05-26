package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Details for one model invocation within a text generation request.
 *
 * <p>A plain generation normally has one step. Tool-enabled generation can have multiple steps:
 * one step where the model asks for tools, followed by another step where the model receives tool
 * results and produces final text.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationStep {
    /**
     * Zero-based step index.
     */
    private Integer stepIndex;
    /**
     * Text produced by this step.
     */
    private String text;
    /**
     * Parsed structured output for this step when requested and available.
     */
    private Object output;
    /**
     * Raw text used to parse structured output for this step.
     */
    private String outputText;
    /**
     * Reasoning text produced by this step when available.
     */
    private String reasoningText;
    /**
     * Structured content parts produced or appended during this step.
     */
    private List<GenerationContentPart> content;
    /**
     * Reasoning parts produced by this step when available.
     */
    private List<ReasoningPart> reasoning;
    /**
     * Normalized finish reason for this step.
     */
    private FinishReason finishReason;
    /**
     * Provider-specific finish reason before normalization.
     */
    private String rawFinishReason;
    /**
     * Token usage for this step when available.
     */
    private LanguageModelUsage usage;
    /**
     * Tool calls requested by the model during this step.
     */
    private List<ToolCall> toolCalls;
    /**
     * Successful tool results produced during this step.
     */
    private List<ToolResult> toolResults;
    /**
     * Tool execution errors produced during this step.
     */
    private List<ToolError> toolErrors;
    /**
     * Non-fatal warnings for this step, such as skipped tool execution.
     */
    private List<GenerationWarning> warnings;
    /**
     * Request metadata for this step.
     */
    private GenerationRequestMetadata request;
    /**
     * Response metadata for this step.
     */
    private GenerationResponseMetadata response;
    /**
     * Provider-specific metadata for this step.
     */
    private Map<String, Object> providerMetadata;
}
