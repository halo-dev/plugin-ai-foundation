package run.halo.aifoundation.service.usage;

import java.time.Instant;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.observation.UsageStatus;

public record UsageQuery(
    Instant from,
    Instant to,
    String callerPlugin,
    String feature,
    String providerName,
    String modelName,
    String modelType,
    String operation,
    UsageStatus status,
    UsageQuality usageQuality,
    UsageTrendResolution resolution
) {

    public UsageQuery {
        if (from == null) {
            throw new IllegalArgumentException("from must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("to must not be null");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    public UsageTrendResolution effectiveResolution() {
        return resolution == null ? UsageTrendResolution.DAY : resolution;
    }
}
