package run.halo.aifoundation.endpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Result of applying one batch sync operation. */
@Getter
@RequiredArgsConstructor
public enum ModelSyncOutcome {
    CREATED("created"),
    UPDATED("updated"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    SKIPPED("skipped"),
    FAILED("failed");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelSyncOutcome fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Unsupported sync outcome: " + value));
    }

    public static Optional<ModelSyncOutcome> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(outcome -> outcome.value.equals(value))
                .findFirst();
    }
}
