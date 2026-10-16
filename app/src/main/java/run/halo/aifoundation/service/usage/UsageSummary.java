package run.halo.aifoundation.service.usage;

public record UsageSummary(
    long callCount,
    long inProgressCount,
    long successCount,
    long failedCount,
    Long inputTokens,
    Long outputTokens,
    Long cacheReadInputTokens,
    Long cacheCreationInputTokens,
    Long reasoningOutputTokens,
    Long accountedTotalTokens,
    long missingUsageCalls,
    long partialUsageCalls,
    double usageCoverage,
    boolean complete
) {
}
