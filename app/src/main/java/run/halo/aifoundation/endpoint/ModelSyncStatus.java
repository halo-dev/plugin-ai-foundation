package run.halo.aifoundation.endpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Relationship between a discovered remote model and the saved models of the same provider. */
@Getter
@RequiredArgsConstructor
public enum ModelSyncStatus {
    /** No saved model uses the remote model id. */
    NEW("new"),

    /** A saved model exists and is enabled locally. */
    EXISTING("existing"),

    /** A saved model exists but is disabled locally. */
    DISABLED("disabled");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelSyncStatus fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Unsupported sync status: " + value));
    }

    public static Optional<ModelSyncStatus> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst();
    }
}
