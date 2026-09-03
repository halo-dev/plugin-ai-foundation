package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageSummary(
    long callCount,
    long inProgressCount,
    long successCount,
    long failedCount,
    long timedOutCount,
    long cancelledCount,
    long abandonedCount,
    Long inputTokens,
    Long outputTokens,
    Long cacheReadInputTokens,
    Long cacheCreationInputTokens,
    Long reasoningOutputTokens,
    Long accountedTotalTokens,
    long knownUsageCalls,
    long missingUsageCalls,
    double usageCoverage,
    boolean complete,
    String resolution,
    Instant dataFrom,
    Instant dataTo
) {
}
