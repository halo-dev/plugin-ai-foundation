package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import run.halo.aifoundation.extension.ModelParameterMappings;

public enum ModelParameterDomain {
    LANGUAGE("language", ModelType.LANGUAGE, mappings -> mappings.getLanguage() != null),
    EMBEDDING("embedding", ModelType.EMBEDDING, mappings -> mappings.getEmbedding() != null),
    RERANK("rerank", ModelType.RERANK, mappings -> mappings.getRerank() != null),
    IMAGE_GENERATION("imageGeneration", ModelType.IMAGE_GENERATION,
        mappings -> mappings.getImageGeneration() != null);

    private final String value;
    private final ModelType modelType;
    private final Predicate<ModelParameterMappings> presence;

    ModelParameterDomain(String value, ModelType modelType,
        Predicate<ModelParameterMappings> presence) {
        this.value = value;
        this.modelType = modelType;
        this.presence = presence;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public boolean isPresent(ModelParameterMappings mappings) {
        return mappings != null && presence.test(mappings);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelParameterDomain fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported model parameter domain: " + value));
    }

    public static Optional<ModelParameterDomain> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(domain -> domain.value.equals(value))
            .findFirst();
    }
}
