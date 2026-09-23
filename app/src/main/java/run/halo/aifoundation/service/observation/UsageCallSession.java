package run.halo.aifoundation.service.observation;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.StringUtils;
import reactor.util.context.ContextView;

public final class UsageCallSession {

    public static final String REACTOR_CONTEXT_KEY = UsageCallSession.class.getName();

    private final UsageEventSink service;
    private final UsageCallStart start;
    private final Clock clock;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean incomplete = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger missingExecutions = new AtomicInteger();
    private NormalizedUsage executionUsage;
    private int completedExecutions;
    private UsageCallTerminal pendingTerminal;
    private boolean provisionalTerminal;
    private java.time.Instant completedAt;
    private final Map<String, AtomicInteger> attemptSequences = new ConcurrentHashMap<>();
    private final AtomicReference<UsageStatus> latestExecutionStatus = new AtomicReference<>();
    private final AtomicReference<UsageError> latestExecutionError = new AtomicReference<>();
    private final AtomicReference<String> latestResponseModelId = new AtomicReference<>();
    private final AtomicInteger observedGenerationSteps = new AtomicInteger();

    public UsageCallSession(UsageEventSink service, UsageCallStart start, Clock clock) {
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
        if (shouldPreserveExecutionStatus(executionStatus)) {
            finish(executionStatus, latestExecutionError.get(), usage, responseModelId, stepCount);
            return;
        }
        finish(UsageStatus.SUCCEEDED, null, usage, responseModelId, stepCount);
    }

    /** A filtered projection may finish before a cached stream's result metadata is consumed. */
    public void succeedProjection() {
        var status = latestExecutionStatus.get();
        finish(status == null ? UsageStatus.SUCCEEDED : status, latestExecutionError.get(),
            NormalizedUsage.missing(), null, 0, true);
    }

    public void fail(Throwable error, NormalizedUsage usage, int stepCount) {
        var status = UsageError.failureStatus(error);
        var fallback = usage;
        if (NormalizedUsage.isMissing(fallback)) {
            fallback = NormalizedUsage.fromLogicalFailure(error);
        }
        finish(status, UsageError.from(error), fallback, null, stepCount);
    }

    public void cancel() {
        finish(UsageStatus.CANCELLED, null, NormalizedUsage.missing(), null, 0);
    }

    public boolean markIncomplete() {
        return incomplete.compareAndSet(false, true);
    }

    public boolean isIncomplete() {
        return incomplete.get();
    }

    public boolean hasExecutions() {
        return attempts.get() > 0;
    }

    synchronized void recordExecution(String id, UsageUnitKind kind, int unitIndex, int attemptIndex,
        java.time.Instant startedAt, UsageStatus status, Throwable error, String responseModelId,
        NormalizedUsage usage) {
        var normalized = usage == null ? NormalizedUsage.missing() : usage;
        if (normalized.quality() == UsageQuality.MISSING) {
            missingExecutions.incrementAndGet();
        }
        latestExecutionStatus.set(status);
        latestExecutionError.set(UsageError.from(error));
        if (StringUtils.hasText(responseModelId)) {
            latestResponseModelId.set(responseModelId);
        }
        if (kind == UsageUnitKind.GENERATION_STEP) {
            observedGenerationSteps.accumulateAndGet(unitIndex + 1, Math::max);
        }
        executionUsage = executionUsage == null ? normalized
            : NormalizedUsage.sum(java.util.List.of(executionUsage, normalized));
        completedExecutions++;
        service.recordExecution(this, new UsageExecutionRecord(id, start.id(), start.epoch(), kind,
            unitIndex, attemptIndex, startedAt, clock.instant(), status, UsageError.from(error),
            start.requestModelId(), responseModelId, normalized));
        publishTerminalIfReady();
    }

    public UsageCallStart start() {
        return start;
    }

    private synchronized void finish(UsageStatus status, UsageError error, NormalizedUsage usage,
        String responseModelId, int stepCount) {
        finish(status, error, usage, responseModelId, stepCount, false);
    }

    private synchronized void finish(UsageStatus status, UsageError error, NormalizedUsage usage,
        String responseModelId, int stepCount, boolean projection) {
        if (!acceptTerminal(projection)) {
            return;
        }
        // Upsert the same logical call once final metadata arrives; never subscribe for it.
        // Provider executions remain authoritative and can never be replaced by cached usage.
        provisionalTerminal = projection && attempts.get() == 0;
        if (completedAt == null) {
            completedAt = clock.instant();
        }
        var complete = !incomplete.get();
        var resolvedUsage = attempts.get() > 0 ? executionUsage : usage;
        if (resolvedUsage == null) {
            resolvedUsage = NormalizedUsage.missing();
        }
        var resolvedResponseModelId = responseModelId;
        var resolvedStepCount = stepCount;
        if (start.streaming()) {
            if (!StringUtils.hasText(resolvedResponseModelId)) {
                resolvedResponseModelId = latestResponseModelId.get();
            }
            resolvedStepCount = Math.max(resolvedStepCount, observedGenerationSteps.get());
        }
        pendingTerminal = new UsageCallTerminal(start, completedAt, status, error,
            resolvedResponseModelId, resolvedStepCount, attempts.get(), missingExecutions.get(),
            complete, resolvedUsage);
        publishTerminalIfReady();
    }

    private boolean shouldPreserveExecutionStatus(UsageStatus status) {
        if (!start.streaming()) {
            return false;
        }
        if (status == null) {
            return false;
        }
        return status != UsageStatus.SUCCEEDED;
    }

    private boolean acceptTerminal(boolean projection) {
        if (terminal.compareAndSet(false, true)) {
            return true;
        }
        if (!provisionalTerminal) {
            return false;
        }
        if (projection) {
            return false;
        }
        return !hasExecutions();
    }

    private void publishTerminalIfReady() {
        if (pendingTerminal == null) {
            return;
        }
        if (completedExecutions < attempts.get()) {
            return;
        }
        var value = pendingTerminal;
        pendingTerminal = null;
        service.finishCall(this, new UsageCallTerminal(start, value.completedAt(), value.status(),
            value.error(), value.responseModelId(), value.stepCount(), attempts.get(),
            missingExecutions.get(), !incomplete.get(),
            attempts.get() > 0 ? executionUsage : value.usage()));
    }
}
