package run.halo.aifoundation.endpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Batch sync action requested by the Console for one discovered model. */
@Getter
@RequiredArgsConstructor
public enum ModelSyncAction {
    /** Create a saved model from the supplied model payload. */
    CREATE("create"),

    /** Apply the discovery-owned remote profile to an existing saved model. */
    UPDATE("update"),

    /** Enable an existing saved model. */
    ENABLE("enable"),

    /** Disable an existing saved model. */
    DISABLE("disable"),

    /** Do not touch the saved model. */
    IGNORE("ignore");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelSyncAction fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Unsupported sync action: " + value));
    }

    public static Optional<ModelSyncAction> find(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(action -> action.value.equals(value))
                .findFirst();
    }
}
