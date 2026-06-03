package run.halo.aifoundation.tool;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A pending approval request for a model-produced tool call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalRequest {
    /**
     * Stable approval id used by a later approval response.
     */
    private String approvalId;
    /**
     * Original tool call id.
     */
    private String toolCallId;
    /**
     * Requested tool name.
     */
    private String toolName;
    /**
     * Validated parsed tool input.
     */
    private Map<String, Object> input;
    /**
     * Step index that produced this request.
     */
    private Integer stepIndex;
    /**
     * Provider or Halo metadata associated with the request.
     */
    private Map<String, Object> providerMetadata;

    public static ToolApprovalRequest from(ToolCall toolCall, String approvalId,
        Integer stepIndex, Map<String, Object> providerMetadata) {
        return ToolApprovalRequest.builder()
            .approvalId(approvalId)
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .stepIndex(stepIndex)
            .providerMetadata(providerMetadata)
            .build();
    }
}
