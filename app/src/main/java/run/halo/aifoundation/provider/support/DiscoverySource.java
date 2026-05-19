package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscoverySource {
    REMOTE("remote"),
    CATALOG("catalog"),
    RULE("rule"),
    MANUAL("manual");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DiscoverySource fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported discovery source: " + value));
    }

    public static Optional<DiscoverySource> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(source -> source.value.equals(value))
            .findFirst();
    }
}
