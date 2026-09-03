package run.halo.aifoundation.service.usage;

import java.time.Clock;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import reactor.util.context.ContextView;

public final class UsageCallSession {

    public static final String REACTOR_CONTEXT_KEY = UsageCallSession.class.getName();

    private final UsageStatisticsService service;
    private final UsageCallStart start;
    private final Clock clock;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean incomplete = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger missingExecutions = new AtomicInteger();
    private final List<NormalizedUsage> executionUsages = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> attemptSequences = new ConcurrentHashMap<>();
    private final AtomicReference<UsageStatus> latestExecutionStatus = new AtomicReference<>();
    private final AtomicReference<UsageError> latestExecutionError = new AtomicReference<>();
    private final AtomicReference<String> latestResponseModelId = new AtomicReference<>();
    private final AtomicInteger observedGenerationSteps = new AtomicInteger();

    UsageCallSession(UsageStatisticsService service, UsageCallStart start, Clock clock) {
        this.service = service;
        this.start = start;
        this.clock = clock;
    }

    public static UsageCallSession from(ContextView context) {
        return context.getOrDefault(REACTOR_CONTEXT_KEY, null);
    }

    public UsageExecutionScope beginExecution(UsageUnitKind kind, int unitIndex) {
        var key = kind.name() + ':' + unitIndex;
        var attemptIndex = attemptSequences.computeIfAbsent(key, ignored -> new AtomicInteger())
            .getAndIncrement();
        attempts.incrementAndGet();
        return new UsageExecutionScope(this, UUID.randomUUID().toString(), kind, unitIndex,
            attemptIndex, clock.instant());
    }

    public void succeed(NormalizedUsage usage, String responseModelId, int stepCount) {
        var executionStatus = latestExecutionStatus.get();
        if (start.streaming() && executionStatus != null
            && executionStatus != UsageStatus.SUCCEEDED) {
            finish(executionStatus, latestExecutionError.get(), usage, responseModelId, stepCount);
            return;
        }
        finish(UsageStatus.SUCCEEDED, null, usage, responseModelId, stepCount);
    }

    public void fail(Throwable error, NormalizedUsage usage, int stepCount) {
        var status = UsageError.isTimeout(error) ? UsageStatus.TIMED_OUT
            : UsageError.isCancellation(error) ? UsageStatus.CANCELLED : UsageStatus.FAILED;
        finish(status, UsageError.from(error), usage, null, stepCount);
    }

    public void cancel() {
        finish(UsageStatus.CANCELLED, null, NormalizedUsage.missing(), null, 0);
    }

    boolean markIncomplete() {
        return incomplete.compareAndSet(false, true);
    }

    boolean isIncomplete() {
        return incomplete.get();
    }

    public boolean hasExecutions() {
        return attempts.get() > 0;
    }

    void recordExecution(String id, UsageUnitKind kind, int unitIndex, int attemptIndex,
        java.time.Instant startedAt, UsageStatus status, Throwable error, String responseModelId,
        NormalizedUsage usage) {
        var normalized = usage == null ? NormalizedUsage.missing() : usage;
        if (normalized.quality() == UsageQuality.MISSING) {
            missingExecutions.incrementAndGet();
        }
        latestExecutionStatus.set(status);
        latestExecutionError.set(UsageError.from(error));
        if (responseModelId != null && !responseModelId.isBlank()) {
            latestResponseModelId.set(responseModelId);
        }
        if (kind == UsageUnitKind.GENERATION_STEP) {
            observedGenerationSteps.accumulateAndGet(unitIndex + 1, Math::max);
        }
        executionUsages.add(normalized);
        service.recordExecution(this, new UsageExecutionRecord(id, start.id(), start.epoch(), kind,
            unitIndex, attemptIndex, startedAt, clock.instant(), status, UsageError.from(error),
            start.requestModelId(), responseModelId, normalized));
    }

    UsageCallStart start() {
        return start;
    }

    private void finish(UsageStatus status, UsageError error, NormalizedUsage usage,
        String responseModelId, int stepCount) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        var complete = !incomplete.get();
        var executionUsage = NormalizedUsage.sum(executionUsages);
        var resolvedUsage = attempts.get() > 0 && executionUsage.quality() != UsageQuality.MISSING
            ? executionUsage : usage;
        if (resolvedUsage == null) {
            resolvedUsage = NormalizedUsage.missing();
        }
        var resolvedResponseModelId = responseModelId;
        var resolvedStepCount = stepCount;
        if (start.streaming()) {
            if (resolvedResponseModelId == null || resolvedResponseModelId.isBlank()) {
                resolvedResponseModelId = latestResponseModelId.get();
            }
            resolvedStepCount = Math.max(resolvedStepCount, observedGenerationSteps.get());
        }
        service.finishCall(this, new UsageCallTerminal(start, clock.instant(), status, error,
            resolvedResponseModelId, resolvedStepCount, attempts.get(), missingExecutions.get(),
            complete, resolvedUsage));
    }
}
