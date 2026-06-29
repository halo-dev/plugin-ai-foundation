package run.halo.aifoundation.service.language.mapping;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public final class LanguageModelMessageMapper {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final String providerType;

    public LanguageModelMessageMapper(String providerType) {
        this.providerType = providerType;
    }

    public org.springframework.ai.chat.messages.Message convert(ModelMessage message) {
        return switch (message.getRole()) {
            case SYSTEM -> new SystemMessage(textContent(message));
            case ASSISTANT -> assistantMessage(message);
            case USER -> UserMessage.builder()
                .text(textContent(message))
                .media(mediaContent(message))
                .build();
            case TOOL -> toolResponseMessage(message);
        };
    }

    public ToolResponseMessage toolResponseMessage(List<ToolResult> results) {
        var responses = results.stream()
            .map(result -> new ToolResponseMessage.ToolResponse(result.getToolCallId(),
                result.getToolName(), writeJson(result.getResult())))
            .toList();
        return ToolResponseMessage.builder().responses(responses).build();
    }

    public List<ModelMessage> responseMessages(String text, List<ReasoningPart> reasoning,
        List<ToolCall> toolCalls) {
        return responseMessages(text, reasoning, toolCalls, List.of());
    }

    public List<ModelMessage> responseMessages(String text, List<ReasoningPart> reasoning,
        List<ToolCall> toolCalls, List<ToolApprovalRequest> approvals) {
        var parts = new ArrayList<ModelMessagePart>();
        nullSafe(reasoning).stream().map(ModelMessagePart::reasoning).forEach(parts::add);
        if (hasText(text)) {
            parts.add(ModelMessagePart.text(text));
        }
        nullSafe(toolCalls).stream().map(ModelMessagePart::toolCall).forEach(parts::add);
        nullSafe(approvals).stream()
            .map(ModelMessagePart::toolApprovalRequest)
            .forEach(parts::add);
        return parts.isEmpty() ? List.of() : List.of(ModelMessage.assistant(parts));
    }

    public boolean hasProviderToolResponse(ModelMessage message) {
        return message != null && message.getContent() != null
            && message.getContent().stream().anyMatch(part -> PartType.isToolResponse(part.getType()));
    }

    private String textContent(ModelMessage message) {
        return message.getContent().stream()
            .filter(part -> PartType.isText(part.getType()))
            .map(ModelMessagePart::getText)
            .reduce("", String::concat);
    }

    private AssistantMessage assistantMessage(ModelMessage message) {
        var toolCalls = message.getContent().stream()
            .filter(part -> PartType.isToolCall(part.getType()))
            .map(part -> new AssistantMessage.ToolCall(part.getToolCallId(), "function",
                part.getToolName(), writeJson(part.getInput())))
            .toList();
        if ("deepseek".equals(providerType)) {
            return DeepSeekAssistantMessage.builder()
                .content(textContent(message))
                .reasoningContent(reasoningContent(message.getContent()))
                .properties(assistantProperties(message))
                .toolCalls(toolCalls)
                .media(mediaContent(message))
                .build();
        }
        return AssistantMessage.builder()
            .content(textContent(message))
            .properties(assistantProperties(message))
            .toolCalls(toolCalls)
            .media(mediaContent(message))
            .build();
    }

    private List<Media> mediaContent(ModelMessage message) {
        return message.getContent().stream()
            .filter(part -> PartType.isImage(part.getType()) || PartType.isFile(part.getType()))
            .map(this::mediaContent)
            .toList();
    }

    private Media mediaContent(ModelMessagePart part) {
        var content = part.getMedia();
        if (content == null) {
            throw new InvalidMediaContentException("media content must not be null");
        }
        var builder = Media.builder()
            .mimeType(mediaType(content));
        if (hasText(content.getFilename())) {
            builder.name(content.getFilename());
        }
        if (content.isData()) {
            builder.data(content.decodedData());
        } else if (content.isUrl()) {
            builder.data(mediaUri(content));
        } else {
            throw new InvalidMediaContentException("media content must set exactly one of url or data",
                content.getMediaType(), content.getFilename(), null, null);
        }
        return builder.build();
    }

    private MimeType mediaType(DataContent content) {
        try {
            return MimeTypeUtils.parseMimeType(content.getMediaType());
        } catch (IllegalArgumentException e) {
            throw new InvalidMediaContentException("media type must be a valid MIME type",
                content.getMediaType(), content.getFilename(), null, null, e);
        }
    }

    private URI mediaUri(DataContent content) {
        try {
            return URI.create(content.getUrl());
        } catch (IllegalArgumentException e) {
            throw new InvalidMediaContentException("media URL must be a valid URI",
                content.getMediaType(), content.getFilename(), null, null, e);
        }
    }

    private Map<String, Object> assistantProperties(ModelMessage message) {
        var reasoningContent = reasoningContent(message.getContent());
        if (!hasText(reasoningContent)) {
            return Map.of();
        }
        return Map.of("reasoningContent", reasoningContent);
    }

    private ToolResponseMessage toolResponseMessage(ModelMessage message) {
        var responses = message.getContent().stream()
            .filter(part -> PartType.isToolResponse(part.getType()))
            .map(part -> new ToolResponseMessage.ToolResponse(part.getToolCallId(),
                part.getToolName(), PartType.isToolError(part.getType())
                ? part.getErrorText()
                : writeJson(part.getResult())))
            .toList();
        return ToolResponseMessage.builder().responses(responses).build();
    }

    private String reasoningContent(List<ModelMessagePart> parts) {
        return parts.stream()
            .filter(part -> PartType.isReasoning(part.getType()))
            .map(part -> {
                var metadataReasoning = reasoningContent(part.getProviderOptions());
                return hasText(metadataReasoning) ? metadataReasoning : part.getText();
            })
            .filter(LanguageModelMessageMapper::hasText)
            .collect(Collectors.joining());
    }

    private String reasoningContent(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        var direct = firstText(metadata, "reasoningContent", "reasoning_content");
        if (hasText(direct)) {
            return direct;
        }
        var provider = metadata.get(providerType);
        if (provider instanceof Map<?, ?> providerMap) {
            return firstText(providerMap, "reasoningContent", "reasoning_content");
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value != null ? value : Map.of());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("failed to serialize JSON value", e);
        }
    }

    private static String firstText(Map<?, ?> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (var key : keys) {
            var value = source.get(key);
            if (value instanceof String text && hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
