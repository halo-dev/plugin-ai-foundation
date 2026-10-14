package run.halo.aifoundation.service.usage;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

public record UsageTrendPoint(
    Instant bucketStart,
    UsageTrendResolution resolution,
    long callCount,
    Long inputTokens,
    Long outputTokens,
    Long accountedTotalTokens,
    long missingUsageCalls,
    long partialUsageCalls,
    boolean complete,
    // Only populated when the query groups by a dimension; omitted from JSON otherwise.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Map<String, UsageTrendDimensionValue> byDimension
) {
}
