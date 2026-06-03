package run.halo.aifoundation.service.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.GenerationStep;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.message.ModelMessageRole;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.service.language.mapping.LanguageModelMessageMapper;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

public final class GenerationMessageHistoryAssembler {
    private final String providerType;
    private final boolean reasoningHistorySupported;
    private final LanguageModelMessageMapper messageMapper;

    public GenerationMessageHistoryAssembler(String providerType,
        boolean reasoningHistorySupported, LanguageModelMessageMapper messageMapper) {
        this.providerType = providerType;
        this.reasoningHistorySupported = reasoningHistorySupported;
        this.messageMapper = messageMapper;
    }

    public GenerationResponseMetadata withToolCalls(GenerationResponseMetadata response,
        String text, List<ReasoningPart> reasoning, List<ToolCall> toolCalls) {
        var messages = messageMapper.responseMessages(text, reasoning, toolCalls);
        if (response == null) {
            return sanitize(GenerationResponseMetadata.builder()
                .messages(messages)
                .metadata(Map.of("providerType", providerType))
                .build());
        }
        return sanitize(copy(response, messages));
    }

    public GenerationResponseMetadata appendApprovalMessages(GenerationResponseMetadata response,
        List<ToolApprovalRequest> approvals) {
        if (approvals == null || approvals.isEmpty()) {
            return response;
        }
        var messages = mutableMessages(response);
        messages.add(ModelMessage.assistant(approvals.stream()
            .map(ModelMessagePart::toolApprovalRequest)
            .toList()));
        return response == null
            ? GenerationResponseMetadata.builder().messages(messages).build()
            : copy(response, messages);
    }

    public GenerationResponseMetadata appendToolMessages(GenerationResponseMetadata response,
        List<ToolResult> results, List<ToolError> errors) {
        if ((results == null || results.isEmpty()) && (errors == null || errors.isEmpty())) {
            return response;
        }
        var parts = new ArrayList<ModelMessagePart>();
        nullSafe(results).stream().map(ModelMessagePart::toolResult).forEach(parts::add);
        nullSafe(errors).stream().map(ModelMessagePart::toolError).forEach(parts::add);
        var messages = mutableMessages(response);
        messages.add(ModelMessage.tool(parts));
        return response == null
            ? GenerationResponseMetadata.builder().messages(messages).build()
            : copy(response, messages);
    }

    public List<ModelMessage> responseMessages(GenerationResponseMetadata response) {
        return response != null && response.getMessages() != null
            ? List.copyOf(response.getMessages())
            : List.of();
    }

    public List<ModelMessage> responseMessages(List<GenerationStep> steps) {
        return nullSafe(steps).stream()
            .flatMap(step -> nullSafe(step.getResponseMessages()).stream())
            .toList();
    }

    public GenerationResponseMetadata sanitize(GenerationResponseMetadata response) {
        if (response == null || reasoningHistorySupported
            || response.getMessages() == null || response.getMessages().isEmpty()) {
            return response;
        }
        var messages = response.getMessages().stream()
            .map(this::withoutReasoningParts)
            .filter(message -> !message.getContent().isEmpty())
            .toList();
        return copy(response, messages);
    }

    private ModelMessage withoutReasoningParts(ModelMessage message) {
        if (message.getRole() != ModelMessageRole.ASSISTANT) {
            return message;
        }
        return ModelMessage.assistant(message.getContent().stream()
            .filter(part -> !PartType.isReasoning(part.getType()))
            .toList());
    }

    private ArrayList<ModelMessage> mutableMessages(GenerationResponseMetadata response) {
        var messages = new ArrayList<ModelMessage>();
        if (response != null && response.getMessages() != null) {
            messages.addAll(response.getMessages());
        }
        return messages;
    }

    private GenerationResponseMetadata copy(GenerationResponseMetadata response,
        List<ModelMessage> messages) {
        return GenerationResponseMetadata.builder()
            .id(response.getId())
            .model(response.getModel())
            .timestamp(response.getTimestamp())
            .messages(messages)
            .headers(response.getHeaders())
            .body(response.getBody())
            .metadata(response.getMetadata())
            .build();
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
