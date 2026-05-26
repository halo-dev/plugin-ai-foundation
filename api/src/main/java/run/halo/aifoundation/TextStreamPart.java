package run.halo.aifoundation;

import java.util.Map;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One event in the Halo text stream protocol returned by {@link LanguageModel#streamText}.
 *
 * <p>Each instance uses {@link #type} as a discriminator. Only some fields are meaningful for a
 * given type. Typical text-only order is:
 *
 * <pre>{@code
 * start
 * start-step
 * text-start
 * text-delta*
 * text-end
 * finish-step
 * finish
 * }</pre>
 *
 * <p>A tool-enabled stream may include {@code tool-call}, {@code tool-result}, and
 * {@code tool-error} events between step lifecycle events. Reasoning-capable models may also emit
 * {@code reasoning-start}, {@code reasoning-delta}, and {@code reasoning-end}; these are not
 * answer text deltas. Structured output requests still stream the model text normally; callers can
 * read the JSON text from text events. Callers should ignore unknown {@code type} values so newer
 * protocol events can be introduced without breaking old clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextStreamPart {
    public static final String TYPE_START = PartType.START;
    public static final String TYPE_START_STEP = PartType.START_STEP;
    public static final String TYPE_TEXT_START = PartType.TEXT_START;
    public static final String TYPE_TEXT_DELTA = PartType.TEXT_DELTA;
    public static final String TYPE_TEXT_END = PartType.TEXT_END;
    public static final String TYPE_REASONING_START = PartType.REASONING_START;
    public static final String TYPE_REASONING_DELTA = PartType.REASONING_DELTA;
    public static final String TYPE_REASONING_END = PartType.REASONING_END;
    public static final String TYPE_TOOL_CALL = PartType.TOOL_CALL;
    public static final String TYPE_TOOL_RESULT = PartType.TOOL_RESULT;
    public static final String TYPE_TOOL_ERROR = PartType.TOOL_ERROR;
    public static final String TYPE_FINISH_STEP = PartType.FINISH_STEP;
    public static final String TYPE_FINISH = PartType.FINISH;
    public static final String TYPE_RAW = PartType.RAW;
    public static final String TYPE_ABORT = PartType.ABORT;
    public static final String TYPE_ERROR = PartType.ERROR;

    /**
     * Event discriminator. Use values from {@link PartType}.
     */
    private String type;
    /**
     * Response message id for {@link PartType#START}.
     */
    private String messageId;
    /**
     * Text block id for {@link PartType#TEXT_START}, {@link PartType#TEXT_DELTA}, and
     * {@link PartType#TEXT_END}; reasoning block id for {@link PartType#REASONING_START},
     * {@link PartType#REASONING_DELTA}, and {@link PartType#REASONING_END}.
     */
    private String id;
    /**
     * Zero-based model invocation step index for step lifecycle events.
     */
    private Integer stepIndex;
    /**
     * Incremental text for {@link PartType#TEXT_DELTA}.
     */
    private String delta;
    /**
     * Optional provider signature for reasoning stream events.
     */
    private String signature;
    /**
     * Tool call id for tool call/result/error events.
     */
    private String toolCallId;
    /**
     * Tool name for tool call/result/error events.
     */
    private String toolName;
    /**
     * Parsed tool arguments for {@link PartType#TOOL_CALL}.
     */
    private Map<String, Object> input;
    /**
     * Server-side tool execution result for {@link PartType#TOOL_RESULT}.
     */
    private Object result;
    /**
     * Normalized finish reason for {@link PartType#FINISH_STEP} and {@link PartType#FINISH}.
     */
    private FinishReason finishReason;
    /**
     * Provider-specific finish reason before Halo normalization.
     */
    private String rawFinishReason;
    /**
     * Usage for the current step on {@link PartType#FINISH_STEP}, or aggregate usage on
     * {@link PartType#FINISH}.
     */
    private LanguageModelUsage usage;
    /**
     * Non-fatal warnings for a completed step.
     */
    private List<GenerationWarning> warnings;
    /**
     * Request metadata for a completed step.
     */
    private GenerationRequestMetadata request;
    /**
     * Response metadata for a completed step.
     */
    private GenerationResponseMetadata response;
    /**
     * Provider-specific metadata associated with the stream event.
     */
    private Map<String, Object> providerMetadata;
    /**
     * Error text for {@link PartType#TOOL_ERROR} or {@link PartType#ERROR}.
     */
    private String errorText;
    /**
     * Sanitized diagnostic metadata for {@link PartType#RAW}.
     */
    private Map<String, Object> metadata;

    /**
     * Creates a stream start event.
     */
    public static TextStreamPart start(String messageId) {
        return TextStreamPart.builder().type(TYPE_START).messageId(messageId).build();
    }

    /**
     * Creates a step start event.
     */
    public static TextStreamPart startStep(Integer stepIndex) {
        return TextStreamPart.builder().type(TYPE_START_STEP).stepIndex(stepIndex).build();
    }

    /**
     * Creates a text block start event.
     */
    public static TextStreamPart textStart(String id) {
        return TextStreamPart.builder().type(TYPE_TEXT_START).id(id).build();
    }

    /**
     * Creates a text delta event.
     */
    public static TextStreamPart textDelta(String id, String delta) {
        return TextStreamPart.builder().type(TYPE_TEXT_DELTA).id(id).delta(delta).build();
    }

    /**
     * Creates a text block end event.
     */
    public static TextStreamPart textEnd(String id) {
        return TextStreamPart.builder().type(TYPE_TEXT_END).id(id).build();
    }

    /**
     * Creates a reasoning block start event.
     */
    public static TextStreamPart reasoningStart(String id) {
        return TextStreamPart.builder().type(TYPE_REASONING_START).id(id).build();
    }

    /**
     * Creates a reasoning delta event. Reasoning deltas should not be appended to answer text.
     */
    public static TextStreamPart reasoningDelta(String id, String delta,
        Map<String, Object> providerMetadata) {
        return TextStreamPart.builder()
            .type(TYPE_REASONING_DELTA)
            .id(id)
            .delta(delta)
            .providerMetadata(providerMetadata)
            .build();
    }

    /**
     * Creates a reasoning block end event.
     */
    public static TextStreamPart reasoningEnd(String id) {
        return TextStreamPart.builder().type(TYPE_REASONING_END).id(id).build();
    }

    /**
     * Creates a stream event for a model-requested tool call.
     */
    public static TextStreamPart toolCall(ToolCall toolCall) {
        return TextStreamPart.builder()
            .type(TYPE_TOOL_CALL)
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .providerMetadata(toolCall.getProviderMetadata())
            .build();
    }

    /**
     * Creates a stream event for a successful server-side tool execution.
     */
    public static TextStreamPart toolResult(ToolResult toolResult) {
        return TextStreamPart.builder()
            .type(TYPE_TOOL_RESULT)
            .toolCallId(toolResult.getToolCallId())
            .toolName(toolResult.getToolName())
            .result(toolResult.getResult())
            .providerMetadata(toolResult.getProviderMetadata())
            .build();
    }

    /**
     * Creates a stream event for a failed server-side tool execution.
     */
    public static TextStreamPart toolError(ToolError toolError) {
        return TextStreamPart.builder()
            .type(TYPE_TOOL_ERROR)
            .toolCallId(toolError.getToolCallId())
            .toolName(toolError.getToolName())
            .errorText(toolError.getErrorText())
            .providerMetadata(toolError.getProviderMetadata())
            .build();
    }

    /**
     * Creates a step finish event.
     */
    public static TextStreamPart finishStep(Integer stepIndex, FinishReason finishReason,
        String rawFinishReason, LanguageModelUsage usage, List<GenerationWarning> warnings,
        GenerationRequestMetadata request, GenerationResponseMetadata response,
        Map<String, Object> providerMetadata) {
        return TextStreamPart.builder()
            .type(TYPE_FINISH_STEP)
            .stepIndex(stepIndex)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(usage)
            .warnings(warnings)
            .request(request)
            .response(response)
            .providerMetadata(providerMetadata)
            .build();
    }

    /**
     * Creates a generation finish event.
     */
    public static TextStreamPart finish(FinishReason finishReason, String rawFinishReason,
        LanguageModelUsage usage) {
        return TextStreamPart.builder()
            .type(TYPE_FINISH)
            .finishReason(finishReason)
            .rawFinishReason(rawFinishReason)
            .usage(usage)
            .build();
    }

    /**
     * Creates a sanitized raw diagnostic event.
     */
    public static TextStreamPart raw(Map<String, Object> metadata) {
        return TextStreamPart.builder().type(TYPE_RAW).metadata(metadata).build();
    }

    /**
     * Creates a stream error event.
     */
    public static TextStreamPart error(String errorText) {
        return TextStreamPart.builder().type(TYPE_ERROR).errorText(errorText).build();
    }
}
