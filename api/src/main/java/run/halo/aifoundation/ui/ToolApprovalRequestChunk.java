package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Canonical tool lifecycle chunk requesting caller approval before execution.
 *
 * @param approvalId approval request id
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param input tool input awaiting approval
 * @param providerMetadata provider-specific metadata
 */
public record ToolApprovalRequestChunk(String approvalId, String toolCallId, String toolName,
                                       Object input, Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolApprovalRequestChunk {
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_APPROVAL_REQUEST;
    }
}
