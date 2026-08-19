package run.halo.aifoundation.service.usage;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UsageExecutionScope {

    private final UsageCallSession session;
    private final String id;
    private final UsageUnitKind kind;
    private final int unitIndex;
    private final int attemptIndex;
    private final Instant startedAt;
    private final AtomicBoolean terminal = new AtomicBoolean();

    UsageExecutionScope(UsageCallSession session, String id, UsageUnitKind kind, int unitIndex,
        int attemptIndex, Instant startedAt) {
        this.session = session;
        this.id = id;
        this.kind = kind;
        this.unitIndex = unitIndex;
        this.attemptIndex = attemptIndex;
        this.startedAt = startedAt;
    }

    public void succeed(NormalizedUsage usage, String responseModelId) {
        finish(UsageStatus.SUCCEEDED, null, usage, responseModelId);
    }

    public void fail(Throwable error, NormalizedUsage usage, String responseModelId) {
        var status = UsageError.isTimeout(error) ? UsageStatus.TIMED_OUT
            : UsageError.isCancellation(error) ? UsageStatus.CANCELLED : UsageStatus.FAILED;
        finish(status, error, usage, responseModelId);
    }

    public void cancel(NormalizedUsage usage, String responseModelId) {
        finish(UsageStatus.CANCELLED, null, usage, responseModelId);
    }

    private void finish(UsageStatus status, Throwable error, NormalizedUsage usage,
        String responseModelId) {
        if (terminal.compareAndSet(false, true)) {
            session.recordExecution(id, kind, unitIndex, attemptIndex, startedAt, status, error,
                responseModelId, usage);
        }
    }
}
