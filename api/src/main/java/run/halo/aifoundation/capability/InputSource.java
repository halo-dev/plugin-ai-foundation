package run.halo.aifoundation.capability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Supported source kind for caller-provided media input.
 */
@Getter
@RequiredArgsConstructor
public enum InputSource {
    /**
     * Media content is provided as bytes or base64 data by the caller.
     */
    DATA("data"),

    /**
     * Media content is provided as a URL for providers that support native URL input.
     */
    URL("url");

    /**
     * Stable serialized value used in capability payloads and selector filters.
     */
    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Parses a serialized input source value.
     *
     * @param value serialized value
     * @return matching input source
     * @throws IllegalArgumentException when the value is not supported
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static InputSource fromValue(String value) {
        return find(value)
            .orElseThrow(() -> new IllegalArgumentException("Unsupported input source: " + value));
    }

    /**
     * Finds an input source by its serialized value.
     *
     * @param value serialized value
     * @return matching input source, or empty when the value is {@code null} or unknown
     */
    public static Optional<InputSource> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(source -> source.value.equals(value))
            .findFirst();
    }

}
