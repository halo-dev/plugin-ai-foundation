package run.halo.aifoundation.service.usage;

public record UsageBreakdownItem(
    String value,
    long callCount,
    long failedCount,
    Long inputTokens,
    Long outputTokens,
    Long cacheReadInputTokens,
    Long cacheCreationInputTokens,
    Long reasoningOutputTokens,
    Long accountedTotalTokens
) {
}
