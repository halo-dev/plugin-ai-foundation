package run.halo.aifoundation.provider.support;

import java.util.Set;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;

/** Rejects portable reasoning that reaches a provider without an explicit model mapping. */
public final class ReasoningControlOptions {

    private static final ReasoningControlOptions UNSUPPORTED = new ReasoningControlOptions();

    private ReasoningControlOptions() {
    }

    public static ReasoningControlOptions unsupported() {
        return UNSUPPORTED;
    }

    public boolean enabledSupported() {
        return false;
    }

    public boolean disabledSupported() {
        return false;
    }

    public boolean disabledWithReasoningHistorySupported() {
        return false;
    }

    public Set<ReasoningOptions.Effort> supportedEfforts() {
        return Set.of();
    }

    public Set<String> providerOptionConflictKeys() {
        return Set.of();
    }

    public void validate(String providerType, GenerateTextRequest request) {
        var reasoning = request != null ? request.getReasoning() : null;
        if (reasoning == null || !reasoning.isExplicit()) {
            return;
        }
        throw new IllegalArgumentException(
            "portable reasoning requires an explicit model parameter mapping for provider type: "
                + providerType);
    }
}
