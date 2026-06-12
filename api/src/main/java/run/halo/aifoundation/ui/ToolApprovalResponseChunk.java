package run.halo.aifoundation.ui;

import java.util.Map;

/**
 * Canonical tool lifecycle chunk carrying a caller approval decision.
 *
 * @param approvalId approval request id
 * @param toolCallId stable tool call id
 * @param toolName tool name
 * @param approved approval decision
 * @param reason optional approval or denial reason
 * @param providerMetadata provider-specific metadata
 */
public record ToolApprovalResponseChunk(String approvalId, String toolCallId, String toolName,
                                        Boolean approved, String reason,
                                        Map<String, Object> providerMetadata)
    implements UIMessageChunk {

    public ToolApprovalResponseChunk {
        providerMetadata = providerMetadata == null ? Map.of() : Map.copyOf(providerMetadata);
    }

    @Override
    public String type() {
        return UIMessageChunkType.TOOL_APPROVAL_RESPONSE;
    }
}
