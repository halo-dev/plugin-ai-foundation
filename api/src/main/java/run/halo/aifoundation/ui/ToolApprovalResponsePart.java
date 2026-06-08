package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted caller response to a pending tool approval request.
 *
 * <p>The part is stored in the assistant UI message that contains the approval
 * request. Conversion turns it into a tool-side model message part so the
 * backend tool approval resolver can continue generation from persisted UI
 * message history.
 *
 * @param approvalId stable approval identifier being answered
 * @param toolCallId optional tool call identifier associated with the approval
 * @param toolName optional tool name associated with the approval
 * @param approved whether the caller approved execution
 * @param reason optional approval or denial reason
 * @param providerMetadata provider-specific metadata
 */
public record ToolApprovalResponsePart(String approvalId, String toolCallId, String toolName,
                                       Boolean approved, String reason,
                                       Map<String, Object> providerMetadata)
    implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_APPROVAL_RESPONSE;
    }
}
