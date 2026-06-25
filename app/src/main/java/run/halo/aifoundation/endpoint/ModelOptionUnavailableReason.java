package run.halo.aifoundation.endpoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelOptionUnavailableReason {
    MODEL_DISABLED("model-disabled"),
    PROVIDER_MISSING("provider-missing"),
    PROVIDER_DISABLED("provider-disabled"),
    CAPABILITY_UNSUPPORTED("capability-unsupported");

    private final String value;

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ModelOptionUnavailableReason fromValue(String value) {
        return Arrays.stream(values())
            .filter(reason -> reason.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported unavailable reason: " + value));
    }
}
