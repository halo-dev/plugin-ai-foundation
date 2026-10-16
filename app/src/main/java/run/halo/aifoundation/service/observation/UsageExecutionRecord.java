package run.halo.aifoundation.service.observation;

import java.time.Instant;

public record UsageExecutionRecord(
    String id,
    String callId,
    long epoch,
    UsageUnitKind unitKind,
    int unitIndex,
    int attemptIndex,
    Instant startedAt,
    Instant completedAt,
    UsageStatus status,
    UsageError error,
    String requestModelId,
    String responseModelId,
    NormalizedUsage usage
) {
}
