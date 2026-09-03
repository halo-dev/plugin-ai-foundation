package run.halo.aifoundation.service.usage;

import java.time.Instant;

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
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    public UsageTrendResolution effectiveResolution() {
        return resolution == null ? UsageTrendResolution.DAY : resolution;
    }
}
