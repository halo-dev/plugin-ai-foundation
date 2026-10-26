package run.halo.aifoundation.capability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Source of a capability domain snapshot.
 */
@Getter
@RequiredArgsConstructor
public enum CapabilitySource {
    /**
     * Capability data came directly from provider model metadata.
     */
    REMOTE("remote"),

    /**
     * Capability data came from AI Foundation's built-in provider or adapter knowledge.
     */
    BUILT_IN("built-in"),

    /**
     * Capability data was supplied or overridden by an administrator.
     */
    MANUAL("manual"),

    /**
     * Capability source is not known.
     */
    UNKNOWN("unknown");

    /**
     * Stable serialized value used in resource specs, API payloads, and selector filters.
     */
    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Parses a serialized capability source value.
     *
     * @param value serialized value
     * @return matching source
     * @throws IllegalArgumentException when the value is not supported
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CapabilitySource fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported capability source: "
                + value));
    }

    /**
     * Finds a capability source by its serialized value.
     *
     * @param value serialized value
     * @return matching source, or empty when the value is {@code null} or unknown
     */
    public static Optional<CapabilitySource> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(source -> source.value.equals(value))
            .findFirst();
    }

}
