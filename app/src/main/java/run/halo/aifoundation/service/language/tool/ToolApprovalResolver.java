package run.halo.aifoundation.service.language.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;

public final class ToolApprovalResolver {

    public ApprovalResolution resolve(List<ModelMessage> messages) {
        var requests = new LinkedHashMap<String, PendingApproval>();
        var responses = new ArrayList<ResponseEntry>();
        var consumedApprovalIds = new java.util.HashSet<String>();
        var resolvedToolCallIds = new java.util.HashSet<String>();

        for (var message : nullSafe(messages)) {
            for (var part : nullSafe(message.getContent())) {
                if (PartType.isToolApprovalRequest(part.getType())) {
                    requests.put(part.getApprovalId(), new PendingApproval(part));
                } else if (PartType.isToolApprovalResponse(part.getType())) {
                    responses.add(new ResponseEntry(part));
                } else if (PartType.isToolResponse(part.getType())
                    && part.getToolCallId() != null) {
                    resolvedToolCallIds.add(part.getToolCallId());
                    requests.values().stream()
                        .filter(request -> part.getToolCallId().equals(request.toolCallId()))
                        .map(PendingApproval::approvalId)
                        .forEach(consumedApprovalIds::add);
                }
            }
        }

        var unresolved = new ArrayList<ResolvedApproval>();
        for (var response : responses) {
            var request = requests.get(response.approvalId());
            if (request == null) {
                throw new IllegalArgumentException("tool approval response references unknown approval: "
                    + response.approvalId());
            }
            if (response.toolCallId() != null && !response.toolCallId().isBlank()
                && !response.toolCallId().equals(request.toolCallId())) {
                throw new IllegalArgumentException("tool approval response toolCallId does not match request: "
                    + response.approvalId());
            }
            if (response.toolName() != null && !response.toolName().isBlank()
                && !response.toolName().equals(request.toolName())) {
                throw new IllegalArgumentException("tool approval response toolName does not match request: "
                    + response.approvalId());
            }
            if (consumedApprovalIds.contains(response.approvalId())
                || resolvedToolCallIds.contains(request.toolCallId())) {
                continue;
            }
            unresolved.add(new ResolvedApproval(request, response));
        }
        return new ApprovalResolution(unresolved);
    }

    public String approvalId(ToolCall toolCall) {
        var id = toolCall.getToolCallId();
        if (id == null || id.isBlank()) {
            return "approval_" + Integer.toHexString(System.identityHashCode(toolCall));
        }
        return id.startsWith("approval_") ? id : "approval_" + id;
    }

    private static List<ModelMessage> nullSafe(List<ModelMessage> messages) {
        return messages != null ? messages : List.of();
    }

    private static List<ModelMessagePart> nullSafe(java.util.Collection<ModelMessagePart> parts) {
        return parts != null ? List.copyOf(parts) : List.of();
    }

    public record ApprovalResolution(List<ResolvedApproval> approvals) {
        public boolean isEmpty() {
            return approvals == null || approvals.isEmpty();
        }
    }

    public record ResolvedApproval(PendingApproval request, ResponseEntry response) {
        public ToolCall toolCall() {
            return ToolCall.builder()
                .toolCallId(request.toolCallId())
                .toolName(request.toolName())
                .input(request.input())
                .providerMetadata(request.providerMetadata())
                .build();
        }

        public ToolError deniedError() {
            var reason = response.reason();
            return ToolError.builder()
                .toolCallId(request.toolCallId())
                .toolName(request.toolName())
                .errorText(reason != null && !reason.isBlank()
                    ? "Tool execution denied: " + reason
                    : "Tool execution denied")
                .providerMetadata(response.providerMetadata())
                .build();
        }
    }

    public record PendingApproval(
        String approvalId,
        String toolCallId,
        String toolName,
        Map<String, Object> input,
        Integer stepIndex,
        Map<String, Object> providerMetadata
    ) {
        PendingApproval(ModelMessagePart part) {
            this(part.getApprovalId(), part.getToolCallId(), part.getToolName(),
                part.getInput(), part.getStepIndex(), part.getProviderOptions());
        }

        ToolApprovalRequest toRequest() {
            return ToolApprovalRequest.builder()
                .approvalId(approvalId)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .input(input)
                .stepIndex(stepIndex)
                .providerMetadata(providerMetadata)
                .build();
        }
    }

    public record ResponseEntry(
        String approvalId,
        String toolCallId,
        String toolName,
        Boolean approved,
        String reason,
        Map<String, Object> providerMetadata
    ) {
        ResponseEntry(ModelMessagePart part) {
            this(part.getApprovalId(), part.getToolCallId(), part.getToolName(),
                part.getApproved(), part.getReason(), part.getProviderOptions());
        }

        ToolApprovalResponse toResponse() {
            return ToolApprovalResponse.builder()
                .approvalId(approvalId)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .approved(approved)
                .reason(reason)
                .providerMetadata(providerMetadata)
                .build();
        }
    }
}
