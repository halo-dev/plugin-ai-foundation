package run.halo.aifoundation.provider.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One target result in a provider health report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One target result in a provider health report")
public class ProviderHealthCheck {

    @Schema(description = "Check target")
    private ProviderCheckTarget target;

    @Schema(description = "Check outcome")
    private ProviderCheckResult result;

    @Schema(description = "Failure category when the check failed")
    private ProviderFailureCategory category;

    @Schema(description = "Reason the check was skipped")
    private ProviderSkipReason skipReason;

    @Schema(description = "Safe, human-readable summary")
    private String message;

    @Schema(description = "Sanitized and bounded raw error summary for developer investigation")
    private String detail;

    @Schema(description = "Provider HTTP status code when one was received")
    private Integer httpStatus;

    @Schema(description = "Elapsed time of the check in milliseconds")
    private Long latencyMs;

    @Schema(description = "Time when this target finished")
    private Instant checkedAt;
}
