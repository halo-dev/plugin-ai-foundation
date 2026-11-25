package run.halo.aifoundation.provider.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable classification of a provider health failure.
 *
 * <p>Categories are intentionally protocol-neutral. Provider-specific error codes are not interpreted; the sanitized
 * raw summary remains available for developer investigation.
 */
@Schema(description = "Stable classification of a provider health failure")
public enum ProviderFailureCategory {
    CONFIGURATION,
    AUTHENTICATION,
    NETWORK,
    RATE_LIMITED,
    PROVIDER_ERROR,
    CAPABILITY_UNSUPPORTED,
    UNKNOWN
}
