package run.halo.aifoundation.service.usage;

import java.time.Instant;

public record UsageHealth(
    boolean available,
    boolean complete,
    int queueDepth,
    long droppedEvents,
    long incompleteCalls,
    long writeFailures,
    Instant lastWriteErrorAt,
    Instant affectedSince,
    Instant affectedUntil,
    String migrationError,
    String integrityError
) {
}
