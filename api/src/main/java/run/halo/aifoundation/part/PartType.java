package run.halo.aifoundation.part;

/**
 * Shared protocol part type values used by message, generation content, and stream parts.
 *
 * <p>The public DTOs intentionally keep {@code type} as a string so that OpenAPI clients can
 * tolerate newer part types. Use these constants and helper methods instead of comparing string
 * literals in caller code.
 */
public final class PartType {
    /**
     * Text content part. Relevant field: {@code text}.
     */
    public static final String TEXT = "text";
    /**
     * Tool call part. Relevant fields: {@code toolCallId}, {@code toolName}, {@code input}.
     */
    public static final String TOOL_CALL = "tool-call";
    /**
     * Successful tool result part. Relevant fields: {@code toolCallId}, {@code toolName},
     * {@code result}.
     */
    public static final String TOOL_RESULT = "tool-result";
    /**
     * Failed tool result part. Relevant fields: {@code toolCallId}, {@code toolName},
     * {@code errorText}.
     */
    public static final String TOOL_ERROR = "tool-error";
    /**
     * Reasoning content part. Relevant fields: {@code text}, {@code providerMetadata}.
     */
    public static final String REASONING = "reasoning";
    /**
     * Source reference content or stream part.
     */
    public static final String SOURCE = "source";
    /**
     * Generated file content or stream part.
     */
    public static final String FILE = "file";
    /**
     * Stream event: a model response message has started. Relevant field: {@code messageId}.
     */
    public static final String START = "start";
    /**
     * Stream event: one model invocation step has started. Relevant field: {@code stepIndex}.
     */
    public static final String START_STEP = "start-step";
    /**
     * Stream event: a text block has started. Relevant field: {@code id}.
     */
    public static final String TEXT_START = "text-start";
    /**
     * Stream event: text delta for the current block. Relevant fields: {@code id},
     * {@code delta}.
     */
    public static final String TEXT_DELTA = "text-delta";
    /**
     * Stream event: a text block has ended. Relevant field: {@code id}.
     */
    public static final String TEXT_END = "text-end";
    /**
     * Stream event: a reasoning block has started. Relevant field: {@code id}.
     */
    public static final String REASONING_START = "reasoning-start";
    /**
     * Stream event: reasoning delta for the current block. Relevant fields: {@code id},
     * {@code delta}, {@code providerMetadata}.
     */
    public static final String REASONING_DELTA = "reasoning-delta";
    /**
     * Stream event: a reasoning block has ended. Relevant field: {@code id}.
     */
    public static final String REASONING_END = "reasoning-end";
    /**
     * Stream event: incremental tool input has started. Relevant fields: {@code toolCallId},
     * {@code toolName}, {@code id}.
     */
    public static final String TOOL_INPUT_START = "tool-input-start";
    /**
     * Stream event: incremental tool input delta. Relevant fields: {@code toolCallId},
     * {@code toolName}, {@code id}, {@code delta}.
     */
    public static final String TOOL_INPUT_DELTA = "tool-input-delta";
    /**
     * Stream event: one model invocation step has ended. Includes finish reason, usage, warnings,
     * request metadata, response metadata, and provider metadata when available.
     */
    public static final String FINISH_STEP = "finish-step";
    /**
     * Stream event: the whole generation has ended. Includes aggregate usage when available.
     */
    public static final String FINISH = "finish";
    /**
     * Stream event: sanitized provider diagnostic data. Relevant field: {@code metadata}.
     */
    public static final String RAW = "raw";
    /**
     * Stream event: generation was aborted.
     */
    public static final String ABORT = "abort";
    /**
     * Stream event: generation failed. Relevant field: {@code errorText}.
     */
    public static final String ERROR = "error";

    private PartType() {
    }

    public static boolean isText(String type) {
        return TEXT.equals(type);
    }

    public static boolean isToolCall(String type) {
        return TOOL_CALL.equals(type);
    }

    public static boolean isToolResult(String type) {
        return TOOL_RESULT.equals(type);
    }

    public static boolean isToolError(String type) {
        return TOOL_ERROR.equals(type);
    }

    public static boolean isReasoning(String type) {
        return REASONING.equals(type);
    }

    public static boolean isSource(String type) {
        return SOURCE.equals(type);
    }

    public static boolean isFile(String type) {
        return FILE.equals(type);
    }

    public static boolean isToolResponse(String type) {
        return isToolResult(type) || isToolError(type);
    }
}
