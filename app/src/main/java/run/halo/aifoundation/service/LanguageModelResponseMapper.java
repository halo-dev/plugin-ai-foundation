package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatResponse;
import run.halo.aifoundation.FinishReason;
import run.halo.aifoundation.GenerationContentPart;
import run.halo.aifoundation.GenerationRequestMetadata;
import run.halo.aifoundation.GenerationResponseMetadata;
import run.halo.aifoundation.GenerationWarning;
import run.halo.aifoundation.LanguageModelUsage;
import run.halo.aifoundation.PartType;
import run.halo.aifoundation.ReasoningPart;
import run.halo.aifoundation.TextStreamPart;
import run.halo.aifoundation.ToolCall;

final class LanguageModelResponseMapper {

    private final String providerType;
    private final LanguageModelMessageMapper messageMapper;

    LanguageModelResponseMapper(String providerType, LanguageModelMessageMapper messageMapper) {
        this.providerType = providerType;
        this.messageMapper = messageMapper;
    }

    String extractText(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
            return "";
        }
        return result.getOutput().getText();
    }

    String extractFinishReason(ChatResponse response) {
        var result = response.getResult();
        if (result == null || result.getMetadata() == null) {
            return null;
        }
        return result.getMetadata().getFinishReason();
    }

    boolean isFinish(String rawFinishReason) {
        return rawFinishReason != null && !rawFinishReason.isBlank()
            && !"null".equalsIgnoreCase(rawFinishReason);
    }

    FinishReason mapFinishReason(String rawFinishReason) {
        if (rawFinishReason == null || rawFinishReason.isBlank()
            || "null".equalsIgnoreCase(rawFinishReason)) {
            return FinishReason.UNKNOWN;
        }
        return switch (rawFinishReason.trim().toLowerCase()) {
            case "stop" -> FinishReason.STOP;
            case "length", "max_tokens" -> FinishReason.LENGTH;
            case "content_filter", "safety" -> FinishReason.CONTENT_FILTER;
            case "tool_calls", "tool-call" -> FinishReason.TOOL_CALLS;
            case "error" -> FinishReason.ERROR;
            default -> FinishReason.OTHER;
        };
    }

    LanguageModelUsage mapUsage(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null || metadata.getUsage() == null) {
            return null;
        }
        var usage = metadata.getUsage();
        var input = usage.getPromptTokens();
        var output = usage.getCompletionTokens();
        var total = usage.getTotalTokens();
        if (input == null && output == null && total == null && usage.getNativeUsage() == null) {
            return null;
        }
        return LanguageModelUsage.builder()
            .inputTokens(input)
            .outputTokens(output)
            .reasoningTokens(reasoningTokens(usage.getNativeUsage()))
            .totalTokens(total)
            .raw(usage.getNativeUsage())
            .build();
    }

    Map<String, Object> mapMetadata(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null) {
            return Map.of();
        }
        var map = new LinkedHashMap<String, Object>();
        map.put("providerType", providerType);
        if (hasText(metadata.getId())) {
            map.put("id", metadata.getId());
        }
        if (hasText(metadata.getModel())) {
            map.put("model", metadata.getModel());
        }
        metadata.entrySet().forEach(entry -> map.put(entry.getKey(), sanitizeValue(entry.getValue())));
        return map;
    }

    List<GenerationContentPart> contentParts(String text, List<ReasoningPart> reasoning) {
        var content = new ArrayList<GenerationContentPart>();
        nullSafe(reasoning).stream().map(GenerationContentPart::reasoning).forEach(content::add);
        if (hasText(text)) {
            content.add(GenerationContentPart.text(text));
        }
        return content;
    }

    TextStreamPart streamPart(GenerationContentPart part) {
        if (PartType.isSource(part.getType())) {
            return TextStreamPart.source(part);
        }
        if (PartType.isFile(part.getType())) {
            return TextStreamPart.file(part);
        }
        return TextStreamPart.raw(Map.of("ignoredPartType", part.getType()));
    }

    List<GenerationContentPart> sourceAndFileParts(ChatResponse response) {
        var metadata = responseMetadataValues(response);
        if (metadata.isEmpty()) {
            return List.of();
        }
        var parts = new ArrayList<GenerationContentPart>();
        addSourceParts(parts, metadata.get("sources"));
        addSourceParts(parts, metadata.get("source"));
        addFileParts(parts, metadata.get("files"));
        addFileParts(parts, metadata.get("file"));
        return parts;
    }

    List<GenerationWarning> mapWarnings(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null || !metadata.containsKey("warnings")) {
            return List.of();
        }
        var warnings = metadata.get("warnings");
        if (warnings instanceof Collection<?> collection) {
            return collection.stream()
                .map(this::mapWarning)
                .toList();
        }
        return List.of(mapWarning(warnings));
    }

    GenerationRequestMetadata mapRequestMetadata(ChatResponse response) {
        var metadata = response.getMetadata();
        return GenerationRequestMetadata.builder()
            .model(metadata != null ? metadata.getModel() : null)
            .metadata(Map.of("providerType", providerType))
            .build();
    }

    GenerationResponseMetadata mapResponseMetadata(ChatResponse response, String text,
        List<ReasoningPart> reasoning, List<ToolCall> toolCalls) {
        var metadata = response.getMetadata();
        return GenerationResponseMetadata.builder()
            .id(metadata != null ? metadata.getId() : null)
            .model(metadata != null ? metadata.getModel() : null)
            .messages(messageMapper.responseMessages(text, reasoning, toolCalls))
            .metadata(mapMetadata(response))
            .build();
    }

    List<ReasoningPart> reasoningParts(String reasoning) {
        if (!hasText(reasoning)) {
            return List.of();
        }
        return List.of(ReasoningPart.builder()
            .text(reasoning)
            .providerMetadata(Map.of(providerType, Map.of("reasoning_content", reasoning)))
            .build());
    }

    Map<String, Object> sanitizedRawDiagnostic(ChatResponse response) {
        var metadata = response.getMetadata();
        if (metadata == null) {
            return Map.of();
        }
        var raw = new LinkedHashMap<String, Object>();
        metadata.entrySet().forEach(entry -> {
            var key = entry.getKey();
            if (key != null && isRawDiagnosticKey(key)) {
                raw.put(key, sanitizeValue(entry.getValue()));
            }
        });
        return raw;
    }

    Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sanitized = new LinkedHashMap<String, Object>();
            map.forEach((key, nestedValue) -> {
                var keyString = key != null ? key.toString() : "";
                sanitized.put(keyString, isSensitiveKey(keyString) ? "[REDACTED]"
                    : sanitizeValue(nestedValue));
            });
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private GenerationWarning mapWarning(Object value) {
        if (value instanceof GenerationWarning warning) {
            return warning;
        }
        if (value instanceof Map<?, ?> map) {
            var code = map.get("code");
            var message = map.get("message");
            return GenerationWarning.builder()
                .code(code != null ? code.toString() : null)
                .message(message != null ? message.toString() : null)
                .providerMetadata((Map<String, Object>) sanitizeValue(map))
                .build();
        }
        return GenerationWarning.builder()
            .message(value != null ? value.toString() : null)
            .build();
    }

    private Map<String, Object> responseMetadataValues(ChatResponse response) {
        var values = new LinkedHashMap<String, Object>();
        if (response == null) {
            return values;
        }
        if (response.getMetadata() != null) {
            response.getMetadata().entrySet()
                .forEach(entry -> values.put(entry.getKey(), sanitizeValue(entry.getValue())));
        }
        var result = response.getResult();
        var output = result != null ? result.getOutput() : null;
        if (output != null && output.getMetadata() != null) {
            output.getMetadata()
                .forEach((key, value) -> values.put(key, sanitizeValue(value)));
        }
        return values;
    }

    private void addSourceParts(List<GenerationContentPart> parts, Object value) {
        for (var item : metadataItems(value)) {
            var id = stringValue(item.get("id"));
            var url = firstString(item, "url", "uri", "sourceUrl");
            var title = firstString(item, "title", "name");
            parts.add(GenerationContentPart.source(id, url, title, item));
        }
    }

    private void addFileParts(List<GenerationContentPart> parts, Object value) {
        for (var item : metadataItems(value)) {
            var id = stringValue(item.get("id"));
            var url = firstString(item, "url", "uri", "downloadUrl");
            var title = firstString(item, "title", "name", "filename");
            var mediaType = firstString(item, "mediaType", "mimeType", "contentType");
            var data = item.get("data");
            parts.add(GenerationContentPart.file(id, url, title, mediaType, data, item));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> metadataItems(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) sanitizeValue(item))
                .toList();
        }
        if (value instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) sanitizeValue(map));
        }
        return List.of();
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (var key : keys) {
            var value = stringValue(map.get(key));
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer reasoningTokens(Object nativeUsage) {
        if (nativeUsage == null) {
            return null;
        }
        try {
            var completionTokenDetails = nativeUsage.getClass()
                .getMethod("completionTokenDetails")
                .invoke(nativeUsage);
            if (completionTokenDetails == null) {
                return null;
            }
            var value = completionTokenDetails.getClass()
                .getMethod("reasoningTokens")
                .invoke(completionTokenDetails);
            return value instanceof Integer integer ? integer : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean isRawDiagnosticKey(String key) {
        var normalized = key.toLowerCase();
        return normalized.contains("raw") || normalized.contains("native");
    }

    private boolean isSensitiveKey(String key) {
        var normalized = key.toLowerCase();
        return normalized.contains("apikey")
            || normalized.contains("api_key")
            || normalized.contains("authorization")
            || normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
