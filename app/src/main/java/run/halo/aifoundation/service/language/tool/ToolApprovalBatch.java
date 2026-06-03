package run.halo.aifoundation.service.language.tool;

import java.util.List;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;

public record ToolApprovalBatch(
    List<ToolCall> toolCalls,
    List<ToolCall> executableCalls,
    List<ToolApprovalRequest> approvalRequests,
    List<ToolError> errors,
    List<GenerationWarning> warnings,
    boolean hasPendingExternalCalls
) {
}
