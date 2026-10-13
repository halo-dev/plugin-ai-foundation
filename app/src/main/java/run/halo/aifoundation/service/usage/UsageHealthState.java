package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageHealthState(
    long droppedEvents,
    long incompleteCalls,
    long writeFailures,
    Instant lastWriteErrorAt,
    Instant affectedSince,
    Instant affectedUntil,
    String initializationError,
    String integrityError
) {
}
