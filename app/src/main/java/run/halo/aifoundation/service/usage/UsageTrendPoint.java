package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageTrendPoint(
    Instant bucketStart,
    UsageTrendResolution resolution,
    long callCount,
    Long inputTokens,
    Long outputTokens,
    Long accountedTotalTokens,
    long knownUsageCalls,
    long missingUsageCalls,
    long partialUsageCalls,
    boolean complete
) {

    UsageTrendPoint withComplete(boolean value) {
        return new UsageTrendPoint(bucketStart, resolution, callCount, inputTokens, outputTokens,
            accountedTotalTokens, knownUsageCalls, missingUsageCalls, partialUsageCalls, value);
    }
}
