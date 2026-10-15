package run.halo.aifoundation.service.usage;

public record UsageBreakdownItem(
    String value,
    long callCount,
    long failedCount,
    Long accountedTotalTokens,
    long missingUsageCalls,
    long partialUsageCalls
) {
}
