package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscoveryConfidence {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DiscoveryConfidence fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported discovery confidence: " + value));
    }

    public static Optional<DiscoveryConfidence> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(confidence -> confidence.value.equals(value))
            .findFirst();
    }
}
