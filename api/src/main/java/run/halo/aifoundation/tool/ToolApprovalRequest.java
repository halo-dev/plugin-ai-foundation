package run.halo.aifoundation.tool;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A pending approval request for a model-produced tool call.
 *
 * <p>Show the validated input to the caller and correlate the decision using {@link #approvalId}. Continue with a
 * matching {@link ToolApprovalResponse}; an approval decision does not replace the tool's resource permission checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalRequest {
    /** Stable approval id used by a later approval response. */
    private String approvalId;
    /** Original tool call id. */
    private String toolCallId;
    /** Requested tool name. */
    private String toolName;
    /** Validated parsed tool input. */
    private Map<String, Object> input;
    /** Step index that produced this request. */
    private Integer stepIndex;
    /** Provider or Halo metadata associated with the request. */
    private Map<String, Object> providerMetadata;

    /**
     * Creates an approval request correlated with an existing model call.
     *
     * @param toolCall validated call awaiting approval
     * @param approvalId identity used by the later approval response
     * @param stepIndex generation step that produced the call
     * @param providerMetadata optional provider or Halo metadata
     * @return request containing the original call identity and input
     */
    public static ToolApprovalRequest from(
            ToolCall toolCall, String approvalId, Integer stepIndex, Map<String, Object> providerMetadata) {
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
