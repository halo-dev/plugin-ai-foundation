package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Map;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerationRequestMetadata;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.chat.LanguageModelUsage;

/**
 * Factory methods for Halo UI message stream chunks.
 */
public final class UIMessageChunks {

    private UIMessageChunks() {
    }

    /**
     * Creates a stream start chunk.
     *
     * @param messageId assistant response message id
     * @return start chunk
     */
    public static StartChunk start(String messageId) {
        return new StartChunk(messageId);
    }

    /**
     * Creates a stream start chunk with initial message metadata.
     *
     * @param messageId assistant response message id
     * @param messageMetadata metadata update object
     * @return start chunk
     */
    public static StartChunk start(String messageId, Object messageMetadata) {
        return new StartChunk(messageId, messageMetadata);
    }

    /**
     * Creates a step start lifecycle chunk.
     *
     * @param stepIndex generation step index
     * @return start step chunk
     */
    public static StartStepChunk startStep(Integer stepIndex) {
        return new StartStepChunk(stepIndex);
    }

    /**
     * Creates a text start chunk.
     *
     * @param id text part id
     * @return text start chunk
     */
    public static TextStartChunk textStart(String id) {
        return new TextStartChunk(id);
    }

    /**
     * Creates a text delta chunk.
     *
     * @param id text part id
     * @param delta text delta
     * @return text delta chunk
     */
    public static TextDeltaChunk textDelta(String id, String delta) {
        return new TextDeltaChunk(id, delta);
    }

    /**
     * Creates a text end chunk.
     *
     * @param id text part id
     * @return text end chunk
     */
    public static TextEndChunk textEnd(String id) {
        return new TextEndChunk(id);
    }

    /**
     * Creates a reasoning start chunk.
     *
     * @param id reasoning part id
     * @return reasoning start chunk
     */
    public static ReasoningStartChunk reasoningStart(String id) {
        return new ReasoningStartChunk(id);
    }

    /**
     * Creates a reasoning delta chunk.
     *
     * @param id reasoning part id
     * @param delta reasoning text delta
     * @param providerMetadata provider-specific reasoning metadata
     * @return reasoning delta chunk
     */
    public static ReasoningDeltaChunk reasoningDelta(String id, String delta,
        Map<String, Object> providerMetadata) {
        return new ReasoningDeltaChunk(id, delta, providerMetadata);
    }

    /**
     * Creates a reasoning end chunk.
     *
     * @param id reasoning part id
     * @return reasoning end chunk
     */
    public static ReasoningEndChunk reasoningEnd(String id) {
        return new ReasoningEndChunk(id);
    }

    /**
     * Creates persisted custom data.
     *
     * @param name data part name
     * @param data data payload
     * @return data chunk
     */
    public static DataChunk data(String name, Object data) {
        return data(name, data, false);
    }

    /**
     * Creates custom data and controls whether the reader persists it.
     *
     * @param name data part name
     * @param data data payload
     * @param transientData whether the data is stream-only
     * @return data chunk
     */
    public static DataChunk data(String name, Object data, boolean transientData) {
        return new DataChunk(name, data, transientData);
    }

    /**
     * Creates dynamic custom data and controls whether the reader persists it.
     *
     * @param id data part id
     * @param name data part name
     * @param data data payload
     * @param transientData whether the data is stream-only
     * @return data chunk
     */
    public static DataChunk data(String id, String name, Object data, boolean transientData) {
        return new DataChunk(DataChunk.typeFor(name), id, name, data, transientData);
    }

    /**
     * Creates stream-only custom data.
     *
     * @param name data part name
     * @param data data payload
     * @return transient data chunk
     */
    public static DataChunk transientData(String name, Object data) {
        return data(name, data, true);
    }

    /**
     * Creates stream-only dynamic custom data.
     *
     * @param id data part id
     * @param name data part name
     * @param data data payload
     * @return transient data chunk
     */
    public static DataChunk transientData(String id, String name, Object data) {
        return data(id, name, data, true);
    }

    /**
     * Creates a message-level metadata update chunk.
     *
     * @param messageMetadata metadata update object
     * @return metadata chunk
     */
    public static MessageMetadataChunk messageMetadata(Object messageMetadata) {
        return new MessageMetadataChunk(messageMetadata);
    }

    /**
     * Creates a source URL chunk.
     *
     * @param sourceId source id
     * @param url source URL
     * @param title optional source title
     * @param providerMetadata provider-specific metadata
     * @return source URL chunk
     */
    public static SourceUrlChunk sourceUrl(String sourceId, String url, String title,
        Map<String, Object> providerMetadata) {
        return new SourceUrlChunk(sourceId, url, title, providerMetadata);
    }

    /**
     * Creates a file chunk.
     *
     * @param fileId file id
     * @param url file URL
     * @param title optional file title
     * @param mediaType optional media type
     * @param data optional inline data
     * @param providerMetadata provider-specific metadata
     * @return file chunk
     */
    public static FileChunk file(String fileId, String url, String title, String mediaType,
        Object data, Map<String, Object> providerMetadata) {
        return new FileChunk(fileId, url, title, mediaType, data, providerMetadata);
    }

    /**
     * Creates a dynamic tool chunk.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param state tool lifecycle state
     * @param input tool input
     * @param inputTextDelta streamed input text delta
     * @param output tool output
     * @param errorText safe tool error text
     * @param approval approval metadata
     * @param providerMetadata provider-specific metadata
     * @return dynamic tool chunk
     */
    public static ToolChunk tool(String toolCallId, String toolName, ToolPartState state,
        Object input, String inputTextDelta, Object output, String errorText,
        ToolApproval approval, Map<String, Object> providerMetadata) {
        return new ToolChunk(ToolChunk.typeFor(toolName), toolCallId, toolName, state, input,
            inputTextDelta, output, errorText, approval, providerMetadata);
    }

    /**
     * Creates a canonical tool input start chunk.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @return tool input start chunk
     */
    public static ToolInputStartChunk toolInputStart(String toolCallId, String toolName) {
        return new ToolInputStartChunk(toolCallId, toolName);
    }

    /**
     * Creates a canonical streamed tool input delta chunk.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param inputTextDelta streamed input text delta
     * @return tool input delta chunk
     */
    public static ToolInputDeltaChunk toolInputDelta(String toolCallId, String toolName,
        String inputTextDelta) {
        return new ToolInputDeltaChunk(toolCallId, toolName, inputTextDelta);
    }

    /**
     * Creates a canonical completed tool input chunk.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param input parsed tool input
     * @param providerMetadata provider-specific metadata
     * @return tool input available chunk
     */
    public static ToolInputAvailableChunk toolInputAvailable(String toolCallId, String toolName,
        Object input, Map<String, Object> providerMetadata) {
        return new ToolInputAvailableChunk(toolCallId, toolName, input, providerMetadata);
    }

    /**
     * Creates a canonical successful tool output chunk.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param output tool output
     * @param providerMetadata provider-specific metadata
     * @return tool output available chunk
     */
    public static ToolOutputAvailableChunk toolOutputAvailable(String toolCallId, String toolName,
        Object output, Map<String, Object> providerMetadata) {
        return new ToolOutputAvailableChunk(toolCallId, toolName, output, providerMetadata);
    }

    /**
     * Creates a canonical failed tool output chunk.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param errorText safe tool error text
     * @param providerMetadata provider-specific metadata
     * @return tool output error chunk
     */
    public static ToolOutputErrorChunk toolOutputError(String toolCallId, String toolName,
        String errorText, Map<String, Object> providerMetadata) {
        return new ToolOutputErrorChunk(toolCallId, toolName, errorText, providerMetadata);
    }

    /**
     * Creates a canonical tool approval request chunk.
     *
     * @param approvalId approval request id
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param input tool input
     * @param providerMetadata provider-specific metadata
     * @return tool approval request chunk
     */
    public static ToolApprovalRequestChunk toolApprovalRequest(String approvalId,
        String toolCallId, String toolName, Object input, Map<String, Object> providerMetadata) {
        return new ToolApprovalRequestChunk(approvalId, toolCallId, toolName, input,
            providerMetadata);
    }

    /**
     * Creates a canonical tool approval response chunk.
     *
     * @param approvalId approval request id
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param approved approval decision
     * @param reason optional approval or denial reason
     * @param providerMetadata provider-specific metadata
     * @return tool approval response chunk
     */
    public static ToolApprovalResponseChunk toolApprovalResponse(String approvalId,
        String toolCallId, String toolName, Boolean approved, String reason,
        Map<String, Object> providerMetadata) {
        return new ToolApprovalResponseChunk(approvalId, toolCallId, toolName, approved, reason,
            providerMetadata);
    }

    /**
     * Creates a per-step finish diagnostic chunk.
     *
     * @param stepIndex generation step index
     * @param finishReason normalized finish reason
     * @param rawFinishReason provider raw finish reason
     * @param usage token usage
     * @param warnings generation warnings
     * @param request request metadata
     * @param response response metadata
     * @param providerMetadata provider-specific metadata
     * @return finish step chunk
     */
    public static FinishStepChunk finishStep(Integer stepIndex, FinishReason finishReason,
        String rawFinishReason, LanguageModelUsage usage, List<GenerationWarning> warnings,
        GenerationRequestMetadata request, GenerationResponseMetadata response,
        Map<String, Object> providerMetadata) {
        return new FinishStepChunk(stepIndex, finishReason, rawFinishReason, usage, warnings,
            request, response, providerMetadata);
    }

    /**
     * Creates a terminal finish chunk.
     *
     * @param finishReason normalized finish reason
     * @param rawFinishReason provider raw finish reason
     * @param usage token usage
     * @return finish chunk
     */
    public static FinishChunk finish(FinishReason finishReason, String rawFinishReason,
        LanguageModelUsage usage) {
        return new FinishChunk(finishReason, rawFinishReason, usage);
    }

    /**
     * Creates a terminal finish chunk with final message metadata.
     *
     * @param finishReason normalized finish reason
     * @param rawFinishReason provider raw finish reason
     * @param usage token usage
     * @param messageMetadata final metadata update object
     * @return finish chunk
     */
    public static FinishChunk finish(FinishReason finishReason, String rawFinishReason,
        LanguageModelUsage usage, Object messageMetadata) {
        return new FinishChunk(finishReason, rawFinishReason, usage, messageMetadata);
    }

    /**
     * Creates a terminal error chunk.
     *
     * @param errorText caller-facing error text
     * @return error chunk
     */
    public static ErrorChunk error(String errorText) {
        return error(errorText, null, null);
    }

    /**
     * Creates a terminal error chunk with optional metadata.
     *
     * @param errorText caller-facing error text
     * @param stepIndex optional generation step index
     * @param metadata provider-specific metadata
     * @return error chunk
     */
    public static ErrorChunk error(String errorText, Integer stepIndex,
        Map<String, Object> metadata) {
        return new ErrorChunk(errorText, stepIndex, metadata);
    }

    /**
     * Creates a terminal abort chunk.
     *
     * @return abort chunk
     */
    public static AbortChunk abort() {
        return new AbortChunk();
    }
}
