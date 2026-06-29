package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelFeature {
    STREAMING("streaming"),
    VISION("vision"),
    AUDIO_INPUT("audio-input"),
    TOOL_CALL("tool-call"),
    STRUCTURED_OUTPUT("structured-output"),
    REASONING("reasoning");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelFeature fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported model feature: " + value));
    }

    public static Optional<ModelFeature> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(feature -> feature.value.equals(value))
            .findFirst();
    }
}
