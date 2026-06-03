package run.halo.aifoundation.tool;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Caller-supplied response to a pending tool approval request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalResponse {
    /**
     * Approval id from the matching request.
     */
    private String approvalId;
    /**
     * Optional original tool call id for validation.
     */
    private String toolCallId;
    /**
     * Optional tool name for validation.
     */
    private String toolName;
    /**
     * Whether execution is approved.
     */
    private Boolean approved;
    /**
     * Optional reason or context that can be shown to the model when denied.
     */
    private String reason;
    /**
     * Optional serializable metadata supplied by the caller.
     */
    private Map<String, Object> providerMetadata;
}
