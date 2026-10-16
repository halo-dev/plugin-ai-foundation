package run.halo.aifoundation.service.usage;

/**
 * Per-dimension-value usage inside one trend bucket. Only present when the trends query
 * carries a breakdown dimension.
 */
public record UsageTrendDimensionValue(
    long callCount,
    Long accountedTotalTokens
) {
}
