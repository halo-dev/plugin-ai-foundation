package run.halo.aifoundation.service.language.reasoning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import run.halo.aifoundation.part.ReasoningPart;

public final class ReasoningContentExtractor {

    private static final Pattern REASONING_TAG_PATTERN = Pattern.compile(
        "<(think|reasoning)>\\s*(.*?)\\s*</\\1>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final List<String> REASONING_METADATA_KEYS = List.of(
        "reasoning", "reasoningContent", "reasoning_content");

    private final String providerType;
    private final Function<Object, Object> sanitizer;

    public ReasoningContentExtractor(String providerType, Function<Object, Object> sanitizer) {
        this.providerType = providerType;
        this.sanitizer = sanitizer;
    }

    public Extraction extract(String text, Map<String, Object> metadata) {
        var safeText = text != null ? text : "";
        var tagged = extractTaggedReasoning(safeText);
        var metadataReasoning = firstMetadataText(metadata);
        var reasoningText = hasText(metadataReasoning)
            ? metadataReasoning
            : tagged.reasoningText();
        var reasoning = hasText(reasoningText)
            ? List.of(ReasoningPart.builder()
                .text(reasoningText)
                .providerMetadata(reasoningProviderMetadata(metadata))
                .build())
            : List.<ReasoningPart>of();
        return new Extraction(tagged.text(), reasoning);
    }

    private TaggedReasoning extractTaggedReasoning(String text) {
        var matcher = REASONING_TAG_PATTERN.matcher(text);
        var reasoning = new ArrayList<String>();
        var cleaned = new StringBuilder();
        while (matcher.find()) {
            var content = matcher.group(2);
            if (hasText(content)) {
                reasoning.add(content.trim());
            }
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);
        return new TaggedReasoning(cleaned.toString().trim(), String.join("", reasoning));
    }

    @SuppressWarnings("unchecked")
    private String firstMetadataText(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (var key : REASONING_METADATA_KEYS) {
            var value = metadata.get(key);
            if (value instanceof String text && hasText(text)) {
                return text;
            }
            if (value instanceof Map<?, ?> map) {
                var nested = firstMetadataText((Map<String, Object>) sanitize(map));
                if (hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Map<String, Object> reasoningProviderMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        var providerValues = new LinkedHashMap<String, Object>();
        metadata.forEach((key, value) -> {
            if (isPreservedReasoningMetadataKey(key)) {
                providerValues.put(key, sanitize(value));
            }
        });
        return providerValues.isEmpty() ? Map.of() : Map.of(providerType, providerValues);
    }

    private boolean isPreservedReasoningMetadataKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        var normalized = key.toLowerCase(Locale.ROOT);
        if ("reasoning".equals(normalized)
            || "reasoningcontent".equals(normalized)
            || "reasoning_content".equals(normalized)) {
            return false;
        }
        return normalized.contains("reasoning") || "signature".equals(normalized);
    }

    private Object sanitize(Object value) {
        return sanitizer != null ? sanitizer.apply(value) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TaggedReasoning(String text, String reasoningText) {
    }

    public record Extraction(String text, List<ReasoningPart> reasoning) {

        public String reasoningText() {
            return reasoning.stream()
                .map(ReasoningPart::getText)
                .filter(ReasoningContentExtractor::hasText)
                .reduce("", String::concat);
        }
    }
}
