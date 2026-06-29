package run.halo.aifoundation.service.language.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.exception.MediaContentTooLargeException;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.service.capability.CapabilityMatchIssue;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.tool.ToolChoice;

public final class LanguageModelRequestValidator {

    private final String providerType;
    private final boolean reasoningHistorySupported;
    private final ModelCapabilities modelCapabilities;
    private final String modelName;
    private final String providerName;
    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;

    public LanguageModelRequestValidator(String providerType, boolean reasoningHistorySupported) {
        this(providerType, reasoningHistorySupported, ModelCapabilities.empty(), null, null,
            new MediaResourcePolicy(), new ModelCapabilityMatcher());
    }

    public LanguageModelRequestValidator(String providerType, boolean reasoningHistorySupported,
        ModelCapabilities modelCapabilities, String modelName, String providerName,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        this.providerType = providerType;
        this.reasoningHistorySupported = reasoningHistorySupported;
        this.modelCapabilities = modelCapabilities != null ? modelCapabilities
            : ModelCapabilities.empty();
        this.modelName = modelName;
        this.providerName = providerName;
        this.mediaResourcePolicy = mediaResourcePolicy != null ? mediaResourcePolicy
            : new MediaResourcePolicy();
        this.capabilityMatcher = capabilityMatcher != null ? capabilityMatcher
            : new ModelCapabilityMatcher();
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
            for (var index = 0; index < request.getMessages().size(); index++) {
                validateMessage(request.getMessages().get(index), index);
            }
            validateToolResponseHistory(request.getMessages());
            validateMediaInputs(request.getMessages());
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

    private void validateMessage(ModelMessage message, int messageIndex) {
        if (message == null) {
            throw new IllegalArgumentException("messages must not contain null items");
        }
        if (message.getRole() == null) {
            throw new IllegalArgumentException("message role must not be null");
        }
        if (message.getContent() == null || message.getContent().isEmpty()) {
            throw new IllegalArgumentException("message content must not be empty");
        }
        for (var partIndex = 0; partIndex < message.getContent().size(); partIndex++) {
            validatePart(message.getRole(), message.getContent().get(partIndex), messageIndex,
                partIndex);
        }
    }

    private void validatePart(ModelMessageRole role, ModelMessagePart part, int messageIndex,
        int partIndex) {
        if (part == null) {
            throw new IllegalArgumentException("message content must not contain null parts");
        }
        if (PartType.isText(part.getType())) {
            validateTextPart(part);
            return;
        }
        if (PartType.isReasoning(part.getType())) {
            validateReasoningPart(role, part);
            return;
        }
        if (PartType.isImage(part.getType()) || PartType.isFile(part.getType())) {
            validateMediaPart(role, part, messageIndex, partIndex);
            return;
        }
        if (role == ModelMessageRole.ASSISTANT && PartType.isToolCall(part.getType())) {
            validateToolCallPart(part);
            return;
        }
        if (role == ModelMessageRole.TOOL && PartType.isToolResponse(part.getType())) {
            validateToolResponsePart(part);
            return;
        }
        if (role == ModelMessageRole.ASSISTANT
            && PartType.isToolApprovalRequest(part.getType())) {
            validateToolApprovalRequestPart(part);
            return;
        }
        if (role == ModelMessageRole.TOOL
            && PartType.isToolApprovalResponse(part.getType())) {
            validateToolApprovalResponsePart(part);
            return;
        }
        throw new IllegalArgumentException("unsupported content part type: " + part.getType());
    }

    private void validateMediaPart(ModelMessageRole role, ModelMessagePart part, int messageIndex,
        int partIndex) {
        if (role != ModelMessageRole.USER && role != ModelMessageRole.ASSISTANT) {
            throw new IllegalArgumentException(
                "media content part is only supported for user or assistant messages");
        }
        if (part.getMedia() == null) {
            throw new InvalidMediaContentException("media content must not be null", null, null,
                messageIndex, partIndex);
        }
    }

    private void validateMediaInputs(List<ModelMessage> messages) {
        var contexts = mediaPartContexts(messages);
        if (contexts.isEmpty()) {
            return;
        }
        contexts.forEach(this::validateMediaShape);
        validateMediaResourceLimits(contexts);
        contexts.forEach(this::validateMediaCapability);
    }

    private List<MediaPartContext> mediaPartContexts(List<ModelMessage> messages) {
        var contexts = new ArrayList<MediaPartContext>();
        for (var messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
            var message = messages.get(messageIndex);
            for (var partIndex = 0; partIndex < message.getContent().size(); partIndex++) {
                var part = message.getContent().get(partIndex);
                if (part != null
                    && (PartType.isImage(part.getType()) || PartType.isFile(part.getType()))) {
                    contexts.add(new MediaPartContext(messageIndex, partIndex, part));
                }
            }
        }
        return contexts;
    }

    private void validateMediaShape(MediaPartContext context) {
        var media = context.media();
        if (media == null) {
            throw invalidMedia("media content must not be null", null, context, null);
        }
        if (media.isUrl() == media.isData()) {
            throw invalidMedia("media content must set exactly one of url or data", media,
                context, null);
        }
        if (!hasText(media.getMediaType())) {
            throw invalidMedia("media type is required for language media input", media, context,
                null);
        }
        if (PartType.isImage(context.part().getType()) && !isImageMediaType(media.getMediaType())) {
            throw invalidMedia("image media part requires an image/* media type", media, context,
                null);
        }
    }

    private void validateMediaResourceLimits(List<MediaPartContext> contexts) {
        try {
            mediaResourcePolicy.validate(contexts.stream().map(MediaPartContext::media).toList());
        } catch (InvalidMediaContentException e) {
            var context = resourceContext(contexts, e.getPartIndex());
            throw new InvalidMediaContentException(e.getMessage(), e.getMediaType(),
                e.getFilename(), context == null ? e.getMessageIndex() : context.messageIndex(),
                context == null ? e.getPartIndex() : context.partIndex(), e);
        } catch (MediaContentTooLargeException e) {
            var context = resourceContext(contexts, e.getPartIndex());
            throw new MediaContentTooLargeException(e.getScope(), e.getMaxBytes(),
                e.getActualBytes(), e.getMediaType(), e.getFilename(),
                context == null ? e.getMessageIndex() : context.messageIndex(),
                context == null ? e.getPartIndex() : context.partIndex());
        }
    }

    private void validateMediaCapability(MediaPartContext context) {
        var media = context.media();
        var language = LanguageCapability.builder()
            .imageInput(PartType.isImage(context.part().getType()) ? Boolean.TRUE : null)
            .fileInput(PartType.isFile(context.part().getType()) ? Boolean.TRUE : null)
            .inputMediaTypes(List.of(media.getMediaType()))
            .inputSources(List.of(media.source()))
            .build();
        var requirement = ModelCapabilityRequirement.builder()
            .language(language)
            .build();
        var result = capabilityMatcher.match(modelCapabilities, requirement);
        if (result.matched()) {
            return;
        }
        var issue = result.issues().isEmpty()
            ? new CapabilityMatchIssue("language", requirement, modelCapabilities)
            : result.issues().getFirst();
        throw unsupportedCapability(issue, context);
    }

    private UnsupportedModelCapabilityException unsupportedCapability(CapabilityMatchIssue issue,
        MediaPartContext context) {
        return new UnsupportedModelCapabilityException(resolvedModelName(), resolvedProviderName(),
            providerType, issue.path(), issue.expected(), issue.actual(), context.messageIndex(),
            context.partIndex());
    }

    private InvalidMediaContentException invalidMedia(String message, DataContent media,
        MediaPartContext context, Throwable cause) {
        if (cause == null) {
            return new InvalidMediaContentException(message,
                media == null ? null : media.getMediaType(),
                media == null ? null : media.getFilename(), context.messageIndex(),
                context.partIndex());
        }
        return new InvalidMediaContentException(message, media == null ? null : media.getMediaType(),
            media == null ? null : media.getFilename(), context.messageIndex(), context.partIndex(),
            cause);
    }

    private MediaPartContext resourceContext(List<MediaPartContext> contexts, Integer partIndex) {
        if (partIndex == null || partIndex < 0 || partIndex >= contexts.size()) {
            return null;
        }
        return contexts.get(partIndex);
    }

    private String resolvedModelName() {
        return hasText(modelName) ? modelName : "unknown";
    }

    private String resolvedProviderName() {
        return hasText(providerName) ? providerName : "unknown";
    }

    private boolean isImageMediaType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase().startsWith("image/");
    }

    private void validateTextPart(ModelMessagePart part) {
        if (!hasText(part.getText())) {
            throw new IllegalArgumentException("text content part must not be blank");
        }
    }

    private void validateReasoningPart(ModelMessageRole role, ModelMessagePart part) {
        if (role != ModelMessageRole.ASSISTANT) {
            throw new IllegalArgumentException(
                "reasoning content part is only supported for assistant messages");
        }
        if (!reasoningHistorySupported) {
            throw new IllegalArgumentException("reasoning content is not supported by provider type: "
                + providerType);
        }
        if (!hasText(part.getText()) && (part.getProviderOptions() == null
            || part.getProviderOptions().isEmpty())) {
            throw new IllegalArgumentException(
                "reasoning content part must include text or provider metadata");
        }
    }

    private void validateToolCallPart(ModelMessagePart part) {
        if (!hasText(part.getToolCallId()) || !hasText(part.getToolName())) {
            throw new IllegalArgumentException(
                "tool-call content part must include toolCallId and toolName");
        }
    }

    private void validateToolResponsePart(ModelMessagePart part) {
        if (!hasText(part.getToolCallId()) || !hasText(part.getToolName())) {
            throw new IllegalArgumentException(
                "tool content part must include toolCallId and toolName");
        }
    }

    private void validateToolApprovalRequestPart(ModelMessagePart part) {
        if (!hasText(part.getApprovalId()) || !hasText(part.getToolCallId())
            || !hasText(part.getToolName())) {
            throw new IllegalArgumentException(
                "tool approval request part must include approvalId, toolCallId and toolName");
        }
    }

    private void validateToolApprovalResponsePart(ModelMessagePart part) {
        if (!hasText(part.getApprovalId()) || part.getApproved() == null) {
            throw new IllegalArgumentException(
                "tool approval response part must include approvalId and approved");
        }
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

    private record MediaPartContext(int messageIndex, int partIndex, ModelMessagePart part) {

        DataContent media() {
            return part.getMedia();
        }
    }
}
