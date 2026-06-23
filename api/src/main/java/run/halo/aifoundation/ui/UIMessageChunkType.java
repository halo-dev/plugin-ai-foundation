package run.halo.aifoundation.ui;

/**
 * Protocol type values for UI message chunks and persisted parts.
 */
public final class UIMessageChunkType {

    /** Starts an assistant message stream. */
    public static final String START = "start";
    /** Starts one generation step. */
    public static final String START_STEP = "start-step";
    /** Persisted text part type. */
    public static final String TEXT = "text";
    /** Opens a streamed text block. */
    public static final String TEXT_START = "text-start";
    /** Appends text to an open text block. */
    public static final String TEXT_DELTA = "text-delta";
    /** Closes a streamed text block. */
    public static final String TEXT_END = "text-end";
    /** Opens a streamed reasoning block. */
    public static final String REASONING_START = "reasoning-start";
    /** Persisted reasoning part type. */
    public static final String REASONING = "reasoning";
    /** Appends reasoning text. */
    public static final String REASONING_DELTA = "reasoning-delta";
    /** Closes a streamed reasoning block. */
    public static final String REASONING_END = "reasoning-end";
    /** Custom data chunk or persisted data part type. */
    public static final String DATA = "data";
    /** Message-level metadata update chunk type. */
    public static final String MESSAGE_METADATA = "message-metadata";
    /** Source URL chunk or persisted part type. */
    public static final String SOURCE_URL = "source-url";
    /** Document source chunk or persisted part type. */
    public static final String SOURCE_DOCUMENT = "source-document";
    /** File chunk or persisted part type. */
    public static final String FILE = "file";
    /** Opens streamed tool input. */
    public static final String TOOL_INPUT_START = "tool-input-start";
    /** Appends streamed tool input. */
    public static final String TOOL_INPUT_DELTA = "tool-input-delta";
    /** Complete tool input is available. */
    public static final String TOOL_INPUT_AVAILABLE = "tool-input-available";
    /** Tool output is available. */
    public static final String TOOL_OUTPUT_AVAILABLE = "tool-output-available";
    /** Tool output failed with an error. */
    public static final String TOOL_OUTPUT_ERROR = "tool-output-error";
    /** Legacy tool call stream part type. */
    public static final String TOOL_CALL = "tool-call";
    /** Legacy tool result stream part type. */
    public static final String TOOL_RESULT = "tool-result";
    /** Legacy tool error stream part type. */
    public static final String TOOL_ERROR = "tool-error";
    /** Tool approval request chunk or persisted part type. */
    public static final String TOOL_APPROVAL_REQUEST = "tool-approval-request";
    /** Tool approval response persisted part type. */
    public static final String TOOL_APPROVAL_RESPONSE = "tool-approval-response";
    /** Per-step finish diagnostic chunk type. */
    public static final String FINISH_STEP = "finish-step";
    /** Terminal successful finish chunk type. */
    public static final String FINISH = "finish";
    /** Terminal error chunk type. */
    public static final String ERROR = "error";
    /** Terminal abort chunk type. */
    public static final String ABORT = "abort";

    private UIMessageChunkType() {
    }
}
