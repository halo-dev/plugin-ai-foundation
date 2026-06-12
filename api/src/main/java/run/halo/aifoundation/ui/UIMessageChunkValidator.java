package run.halo.aifoundation.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates UI message stream chunks before they are reduced into message state.
 */
public final class UIMessageChunkValidator {

    private UIMessageChunkValidator() {
    }

    /**
     * Validates one stream chunk.
     *
     * @param chunk chunk to validate
     * @throws InvalidUIMessageException when the chunk violates the protocol
     */
    public static void validate(UIMessageChunk chunk) {
        var issues = safeValidate(chunk);
        if (!issues.isEmpty()) {
            throw new InvalidUIMessageException(issues);
        }
    }

    /**
     * Validates one stream chunk and returns issues instead of throwing.
     *
     * @param chunk chunk to validate
     * @return validation issues
     */
    public static List<UIMessageValidationIssue> safeValidate(UIMessageChunk chunk) {
        var issues = new ArrayList<UIMessageValidationIssue>();
        if (chunk == null) {
            issues.add(issue(null, null, "chunk.required", "UI message chunk must not be null"));
            return issues;
        }
        switch (chunk) {
            case DataChunk data -> validateData(data, issues);
            case ToolInputStartChunk tool -> validateToolIdentity(tool.type(), tool.toolCallId(),
                tool.toolName(), issues);
            case ToolInputDeltaChunk tool -> {
                validateToolIdentity(tool.type(), tool.toolCallId(), tool.toolName(), issues);
                require(tool.type(), tool.toolCallId(), tool.inputTextDelta(),
                    "chunk.tool.input-delta.required",
                    "Tool input delta must not be blank", issues);
            }
            case ToolInputAvailableChunk tool -> validateToolIdentity(tool.type(),
                tool.toolCallId(), tool.toolName(), issues);
            case ToolOutputAvailableChunk tool -> validateToolIdentity(tool.type(),
                tool.toolCallId(), tool.toolName(), issues);
            case ToolOutputErrorChunk tool -> {
                validateToolIdentity(tool.type(), tool.toolCallId(), tool.toolName(), issues);
                require(tool.type(), tool.toolCallId(), tool.errorText(),
                    "chunk.tool.error-text.required", "Tool error text must not be blank",
                    issues);
            }
            case ToolApprovalRequestChunk tool -> {
                validateToolIdentity(tool.type(), tool.toolCallId(), tool.toolName(), issues);
                require(tool.type(), tool.toolCallId(), tool.approvalId(),
                    "chunk.tool.approval-id.required",
                    "Tool approval id must not be blank", issues);
            }
            case ToolApprovalResponseChunk tool -> {
                validateToolIdentity(tool.type(), tool.toolCallId(), tool.toolName(), issues);
                require(tool.type(), tool.toolCallId(), tool.approvalId(),
                    "chunk.tool.approval-id.required",
                    "Tool approval id must not be blank", issues);
                if (tool.approved() == null) {
                    issues.add(issue(tool.type(), tool.toolCallId(),
                        "chunk.tool.approved.required",
                        "Tool approval response approved value must not be null"));
                }
            }
            case ToolChunk tool -> validateTool(tool, issues);
            default -> {
            }
        }
        return List.copyOf(issues);
    }

    private static void validateData(DataChunk data, List<UIMessageValidationIssue> issues) {
        require(data.type(), data.id(), data.type(), "chunk.data.type.required",
            "Data chunk type must not be blank", issues);
        require(data.type(), data.id(), data.id(), "chunk.data.id.required",
            "Data chunk id must not be blank", issues);
        require(data.type(), data.id(), data.name(), "chunk.data.name.required",
            "Data chunk name must not be blank", issues);
        try {
            UIMessageDynamicNames.requireDataType(data.type(), data.name());
        } catch (IllegalArgumentException e) {
            issues.add(issue(data.type(), data.id(), "chunk.data.type.invalid", e.getMessage()));
        }
    }

    private static void validateTool(ToolChunk tool, List<UIMessageValidationIssue> issues) {
        validateToolIdentity(tool.type(), tool.toolCallId(), tool.toolName(), issues);
        if (tool.state() == null) {
            issues.add(issue(tool.type(), tool.toolCallId(), "chunk.tool.state.required",
                "Tool chunk state must not be null"));
            return;
        }
        try {
            UIMessageDynamicNames.requireToolType(tool.type(), tool.toolName());
        } catch (IllegalArgumentException e) {
            issues.add(issue(tool.type(), tool.toolCallId(), "chunk.tool.type.invalid",
                e.getMessage()));
        }
        if (tool.state() == ToolPartState.OUTPUT_ERROR && isBlank(tool.errorText())) {
            issues.add(issue(tool.type(), tool.toolCallId(), "chunk.tool.error-text.required",
                "Tool error text must not be blank"));
        }
    }

    private static void validateToolIdentity(String type, String toolCallId, String toolName,
        List<UIMessageValidationIssue> issues) {
        require(type, toolCallId, type, "chunk.tool.type.required",
            "Tool chunk type must not be blank", issues);
        require(type, toolCallId, toolCallId, "chunk.tool.id.required",
            "Tool call id must not be blank", issues);
        require(type, toolCallId, toolName, "chunk.tool.name.required",
            "Tool name must not be blank", issues);
    }

    private static void require(String partType, String partId, String value, String code,
        String message, List<UIMessageValidationIssue> issues) {
        if (isBlank(value)) {
            issues.add(issue(partType, partId, code, message));
        }
    }

    private static UIMessageValidationIssue issue(String partType, String partId, String code,
        String message) {
        return new UIMessageValidationIssue(null, null, partType, partId, code, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
