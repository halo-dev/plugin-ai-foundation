package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Persisted approval request for a tool call.
 *
 * @param approvalId stable approval identifier
 * @param toolCallId tool call identifier
 * @param toolName tool name
 * @param input proposed tool input
 * @param stepIndex generation step index
 * @param providerMetadata provider-specific metadata
 */
public record ToolApprovalRequestPart(String approvalId, String toolCallId, String toolName,
                                      Map<String, Object> input, Integer stepIndex,
                                      Map<String, Object> providerMetadata)
    implements UIMessagePart {
    @Override
    public String type() {
        return UIMessageChunkType.TOOL_APPROVAL_REQUEST;
    }
}
