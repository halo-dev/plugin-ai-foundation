package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageHealthState(
    long droppedEvents,
    long incompleteCalls,
    long writeFailures,
    Instant lastWriteErrorAt,
    Instant affectedSince,
    Instant affectedUntil,
    String migrationError,
    String integrityError
) {

    public static UsageHealthState empty() {
        return new UsageHealthState(0, 0, 0, null, null, null, null, null);
    }
}
