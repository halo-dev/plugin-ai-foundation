package run.halo.aifoundation.service.language.mapping;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolChoice;

public final class LanguageModelRequestValidator {

    private final String providerType;
    private final boolean reasoningHistorySupported;

    public LanguageModelRequestValidator(String providerType, boolean reasoningHistorySupported) {
        this.providerType = providerType;
        this.reasoningHistorySupported = reasoningHistorySupported;
    }

    public void validate(GenerateTextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.getSystem() != null && request.getSystem().isBlank()) {
            throw new IllegalArgumentException("system must not be blank");
        }
        var hasPrompt = hasText(request.getPrompt());
        var hasMessages = request.getMessages() != null && !request.getMessages().isEmpty();
        if (hasPrompt == hasMessages) {
            throw new IllegalArgumentException("exactly one of prompt or messages must be provided");
        }
        if (request.getPrompt() != null && !hasPrompt) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (hasMessages) {
            for (var message : request.getMessages()) {
                validateMessage(message);
            }
            validateToolResponseHistory(request.getMessages());
        }
        if (request.getMaxRetries() != null && request.getMaxRetries() < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        validateOutput(request.getOutput());
        validateTools(request);
    }

    public void validateTools(GenerateTextRequest request) {
        var tools = request.getTools();
        if (tools == null || tools.isEmpty()) {
            if (request.getToolChoice() != null
                && (request.getToolChoice().getType() == ToolChoice.Type.TOOL
                || request.getToolChoice().getType() == ToolChoice.Type.REQUIRED)) {
                throw new IllegalArgumentException("toolChoice requires tools");
            }
            return;
        }
        var names = new HashSet<String>();
        for (var tool : tools) {
            if (tool == null) {
                throw new IllegalArgumentException("tools must not contain null items");
            }
            if (!hasText(tool.getName()) || !tool.getName().matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("tool name must contain only letters, numbers, '_' or '-'");
            }
            if (!names.add(tool.getName())) {
                throw new IllegalArgumentException("duplicate tool name: " + tool.getName());
            }
            validateSchemaObject(tool.getInputSchema(), "tool inputSchema");
            validateSchemaObject(tool.getOutputSchema(), "tool outputSchema");
        }
        var choice = request.getToolChoice();
        if (choice != null && choice.getType() == ToolChoice.Type.TOOL
            && !hasText(choice.getToolName())) {
            throw new IllegalArgumentException("toolChoice toolName must not be blank");
        }
        if (choice != null && choice.getType() == ToolChoice.Type.TOOL
            && !names.contains(choice.getToolName())) {
            throw new IllegalArgumentException("toolChoice references unknown tool: "
                + choice.getToolName());
        }
    }

    private void validateMessage(ModelMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("messages must not contain null items");
        }
        if (message.getRole() == null) {
            throw new IllegalArgumentException("message role must not be null");
        }
        if (message.getContent() == null || message.getContent().isEmpty()) {
            throw new IllegalArgumentException("message content must not be empty");
        }
        for (var part : message.getContent()) {
            validatePart(message.getRole(), part);
        }
    }

    private void validatePart(ModelMessageRole role, ModelMessagePart part) {
        if (part == null) {
            throw new IllegalArgumentException("message content must not contain null parts");
        }
        if (PartType.isText(part.getType()) && !hasText(part.getText())) {
            throw new IllegalArgumentException("text content part must not be blank");
        }
        if (PartType.isText(part.getType())) {
            return;
        }
        if (PartType.isReasoning(part.getType())) {
            if (role != ModelMessageRole.ASSISTANT) {
                throw new IllegalArgumentException("reasoning content part is only supported for assistant messages");
            }
            if (!reasoningHistorySupported) {
                throw new IllegalArgumentException("reasoning content is not supported by provider type: "
                    + providerType);
            }
            if (!hasText(part.getText()) && (part.getProviderOptions() == null
                || part.getProviderOptions().isEmpty())) {
                throw new IllegalArgumentException("reasoning content part must include text or provider metadata");
            }
            return;
        }
        if (role == ModelMessageRole.ASSISTANT && PartType.isToolCall(part.getType())) {
            if (!hasText(part.getToolCallId()) || !hasText(part.getToolName())) {
                throw new IllegalArgumentException("tool-call content part must include toolCallId and toolName");
            }
            return;
        }
        if (role == ModelMessageRole.TOOL && PartType.isToolResponse(part.getType())) {
            if (!hasText(part.getToolCallId()) || !hasText(part.getToolName())) {
                throw new IllegalArgumentException("tool content part must include toolCallId and toolName");
            }
            return;
        }
        if (role == ModelMessageRole.ASSISTANT
            && PartType.isToolApprovalRequest(part.getType())) {
            if (!hasText(part.getApprovalId()) || !hasText(part.getToolCallId())
                || !hasText(part.getToolName())) {
                throw new IllegalArgumentException(
                    "tool approval request part must include approvalId, toolCallId and toolName");
            }
            return;
        }
        if (role == ModelMessageRole.TOOL
            && PartType.isToolApprovalResponse(part.getType())) {
            if (!hasText(part.getApprovalId()) || part.getApproved() == null) {
                throw new IllegalArgumentException(
                    "tool approval response part must include approvalId and approved");
            }
            return;
        }
        throw new IllegalArgumentException("unsupported content part type: " + part.getType());
    }

    private void validateToolResponseHistory(List<ModelMessage> messages) {
        var toolCallsById = new LinkedHashMap<String, String>();
        for (var message : messages) {
            for (var part : message.getContent()) {
                if (message.getRole() == ModelMessageRole.ASSISTANT
                    && PartType.isToolCall(part.getType())) {
                    toolCallsById.putIfAbsent(part.getToolCallId(), part.getToolName());
                    continue;
                }
                if (message.getRole() == ModelMessageRole.TOOL
                    && PartType.isToolResponse(part.getType())) {
                    var expectedToolName = toolCallsById.get(part.getToolCallId());
                    if (expectedToolName == null) {
                        throw new IllegalArgumentException(
                            "tool response references unknown tool call: "
                                + part.getToolCallId());
                    }
                    if (!expectedToolName.equals(part.getToolName())) {
                        throw new IllegalArgumentException(
                            "tool response toolName mismatch for tool call "
                                + part.getToolCallId() + ": expected " + expectedToolName
                                + " but got " + part.getToolName());
                    }
                }
            }
        }
    }

    private void validateOutput(OutputSpec output) {
        if (output == null || output.getType() == null || output.getType() == OutputType.TEXT) {
            return;
        }
        switch (output.getType()) {
            case OBJECT -> validateRequiredSchemaObject(output.getSchema(), "output schema");
            case ARRAY -> validateRequiredSchemaObject(output.getElementSchema(), "output elementSchema");
            case CHOICE -> {
                if (output.getChoices() == null || output.getChoices().isEmpty()) {
                    throw new IllegalArgumentException("output choices must not be empty");
                }
                if (output.getChoices().stream().anyMatch(choice -> !hasText(choice))) {
                    throw new IllegalArgumentException("output choices must not contain blank values");
                }
            }
            case JSON -> {
            }
            default -> {
            }
        }
    }

    private void validateSchemaObject(Object schema, String name) {
        if (schema == null) {
            return;
        }
        if (!(schema instanceof java.util.Map<?, ?>)) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
    }

    private void validateRequiredSchemaObject(Object schema, String name) {
        if (!(schema instanceof java.util.Map<?, ?> map) || map.isEmpty()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
