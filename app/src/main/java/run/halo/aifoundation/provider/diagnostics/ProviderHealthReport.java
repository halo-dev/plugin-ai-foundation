package run.halo.aifoundation.provider.diagnostics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.extension.AiProvider;

/** Latest result of a provider connectivity check. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Latest structured provider health report")
public class ProviderHealthReport {

    @Schema(description = "AiProvider.metadata.name")
    private String providerName;

    @Schema(description = "Overall provider phase")
    private AiProvider.AiProviderStatus.Phase phase;

    @Schema(description = "Failure category of the primary failed check, or null when no check failed")
    private ProviderFailureCategory failureCategory;

    @Schema(description = "Safe summary of the primary failure or of the successful check")
    private String message;

    @Schema(description = "Time when the report was produced")
    private Instant checkedAt;

    @Schema(description = "Per-target check results in a stable order")
    private List<ProviderHealthCheck> checks;
}
