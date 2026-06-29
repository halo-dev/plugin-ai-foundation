package run.halo.aifoundation.capability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Top-level capability domains tracked by AI Foundation.
 */
@Getter
@RequiredArgsConstructor
public enum CapabilityDomain {
    /**
     * Language model capabilities, including media input and reasoning-history support.
     */
    LANGUAGE("language"),

    /**
     * Image generation capabilities, including generation modes and output controls.
     */
    IMAGE_GENERATION("imageGeneration");

    /**
     * Stable serialized value used in resource specs, API payloads, and selector filters.
     */
    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Parses a serialized capability domain value.
     *
     * @param value serialized value
     * @return matching domain
     * @throws IllegalArgumentException when the value is not supported
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CapabilityDomain fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported capability domain: "
                + value));
    }

    /**
     * Finds a capability domain by its serialized value.
     *
     * @param value serialized value
     * @return matching domain, or empty when the value is {@code null} or unknown
     */
    public static Optional<CapabilityDomain> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(domain -> domain.value.equals(value))
            .findFirst();
    }

}
