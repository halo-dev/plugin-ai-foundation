package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdapterType {
    OPENAI_CHAT("openai-chat", ModelType.LANGUAGE),
    OPENAI_EMBEDDING("openai-embedding", ModelType.EMBEDDING),
    OPENAI_IMAGE("openai-image", ModelType.IMAGE_GENERATION),
    ANTHROPIC_MESSAGES("anthropic-messages", ModelType.LANGUAGE),
    GEMINI_GENERATE_CONTENT("gemini-generate-content", ModelType.LANGUAGE),
    GEMINI_EMBED_CONTENT("gemini-embed-content", ModelType.EMBEDDING),
    RERANK("rerank", ModelType.RERANK),
    OLLAMA_CHAT("ollama-chat", ModelType.LANGUAGE),
    OLLAMA_EMBEDDING("ollama-embedding", ModelType.EMBEDDING);

    private final String value;
    private final ModelType modelType;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AdapterType fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported adapterType: " + value));
    }

    public static Optional<AdapterType> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(adapter -> adapter.value.equals(value))
            .findFirst();
    }

    public static Optional<AdapterType> firstFor(Collection<AdapterType> adapters,
        ModelType modelType) {
        if (adapters == null || modelType == null) {
            return Optional.empty();
        }
        return adapters.stream()
            .filter(adapter -> adapter.modelType == modelType)
            .findFirst();
    }
}
