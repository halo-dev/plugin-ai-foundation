package run.halo.aifoundation.provider.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;

/** Outcome of one provider health check target. */
@Schema(description = "Result of an individual provider health check")
public enum ProviderCheckResult {
    PASSED,
    FAILED,
    SKIPPED
}
