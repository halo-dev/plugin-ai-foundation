package run.halo.aifoundation.provider.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why a provider health check target did not run. */
@Schema(description = "Reason a provider health check target was skipped")
public enum ProviderSkipReason {
    NOT_REQUESTED,
    NO_MODEL,
    PROVIDER_DISABLED,
    BLOCKED_BY_PREVIOUS_FAILURE,
    UNSUPPORTED
}
