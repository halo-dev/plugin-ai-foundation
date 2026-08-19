package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageCallTerminal(
    UsageCallStart start,
    Instant completedAt,
    UsageStatus status,
    UsageError error,
    String responseModelId,
    int stepCount,
    int attemptCount,
    int missingExecutionCount,
    boolean complete,
    NormalizedUsage usage
) {

    public String callId() {
        return start.id();
    }

    public long epoch() {
        return start.epoch();
    }
}
