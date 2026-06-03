package run.halo.aifoundation.lifecycle;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.tool.ToolApprovalRequest;

/**
 * Lifecycle event emitted when a tool call requires external approval before execution.
 */
@Value
@Builder
public class GenerationToolApprovalRequestEvent {
    /**
     * Zero-based generation step index.
     */
    int stepIndex;
    /**
     * Pending approval request.
     */
    ToolApprovalRequest approvalRequest;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
