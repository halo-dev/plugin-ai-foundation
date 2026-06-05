package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Factory methods for persisted UI message parts.
 */
public final class UIMessageParts {

    private UIMessageParts() {
    }

    /**
     * Creates a persisted text part.
     *
     * @param id text part id
     * @param text text content
     * @return text part
     */
    public static TextPart text(String id, String text) {
        return new TextPart(id, text);
    }

    /**
     * Creates a persisted reasoning part.
     *
     * @param id reasoning part id
     * @param text reasoning text
     * @param providerMetadata provider-specific reasoning metadata
     * @return reasoning part
     */
    public static ReasoningPart reasoning(String id, String text,
        Map<String, Object> providerMetadata) {
        return new ReasoningPart(id, text, providerMetadata);
    }

    /**
     * Creates a persisted custom data part.
     *
     * @param name data part name
     * @param data data payload
     * @return data part
     */
    public static DataPart data(String name, Object data) {
        return new DataPart(name, data);
    }

    /**
     * Creates a persisted source URL part.
     *
     * @param sourceId source id
     * @param url source URL
     * @param title optional source title
     * @param providerMetadata provider-specific metadata
     * @return source URL part
     */
    public static SourceUrlPart sourceUrl(String sourceId, String url, String title,
        Map<String, Object> providerMetadata) {
        return new SourceUrlPart(sourceId, url, title, providerMetadata);
    }

    /**
     * Creates a persisted file part.
     *
     * @param fileId file id
     * @param url file URL
     * @param title optional file title
     * @param mediaType optional media type
     * @param data optional inline data
     * @param providerMetadata provider-specific metadata
     * @return file part
     */
    public static FilePart file(String fileId, String url, String title, String mediaType,
        Object data, Map<String, Object> providerMetadata) {
        return new FilePart(fileId, url, title, mediaType, data, providerMetadata);
    }

    /**
     * Creates a persisted tool call part.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param input tool input
     * @param providerMetadata provider-specific metadata
     * @return tool call part
     */
    public static ToolCallPart toolCall(String toolCallId, String toolName,
        Map<String, Object> input, Map<String, Object> providerMetadata) {
        return new ToolCallPart(toolCallId, toolName, input, providerMetadata);
    }

    /**
     * Creates a persisted tool result part.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param result tool result payload
     * @param providerMetadata provider-specific metadata
     * @return tool result part
     */
    public static ToolResultPart toolResult(String toolCallId, String toolName, Object result,
        Map<String, Object> providerMetadata) {
        return new ToolResultPart(toolCallId, toolName, result, providerMetadata);
    }

    /**
     * Creates a persisted tool error part.
     *
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param errorText caller-facing error text
     * @param providerMetadata provider-specific metadata
     * @return tool error part
     */
    public static ToolErrorPart toolError(String toolCallId, String toolName, String errorText,
        Map<String, Object> providerMetadata) {
        return new ToolErrorPart(toolCallId, toolName, errorText, providerMetadata);
    }

    /**
     * Creates a persisted tool approval request part.
     *
     * @param approvalId approval request id
     * @param toolCallId tool call id
     * @param toolName tool name
     * @param input tool input
     * @param stepIndex optional generation step index
     * @param providerMetadata provider-specific metadata
     * @return tool approval request part
     */
    public static ToolApprovalRequestPart toolApprovalRequest(String approvalId,
        String toolCallId, String toolName, Map<String, Object> input, Integer stepIndex,
        Map<String, Object> providerMetadata) {
        return new ToolApprovalRequestPart(approvalId, toolCallId, toolName, input, stepIndex,
            providerMetadata);
    }

    /**
     * Creates a persisted tool approval response part.
     *
     * @param approvalId approval request id being answered
     * @param toolCallId optional tool call id
     * @param toolName optional tool name
     * @param approved whether execution was approved
     * @param reason optional approval or denial reason
     * @param providerMetadata provider-specific metadata
     * @return tool approval response part
     */
    public static ToolApprovalResponsePart toolApprovalResponse(String approvalId,
        String toolCallId, String toolName, Boolean approved, String reason,
        Map<String, Object> providerMetadata) {
        return new ToolApprovalResponsePart(approvalId, toolCallId, toolName, approved, reason,
            providerMetadata);
    }
}
