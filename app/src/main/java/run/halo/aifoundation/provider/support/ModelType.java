package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelType {
    LANGUAGE("language"),
    EMBEDDING("embedding"),
    RERANK("rerank"),
    IMAGE_GENERATION("image-generation");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelType fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported modelType: " + value));
    }

    public static Optional<ModelType> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(type -> type.value.equals(value))
            .findFirst();
    }
}
