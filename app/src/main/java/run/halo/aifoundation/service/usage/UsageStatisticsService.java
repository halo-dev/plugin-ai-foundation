package run.halo.aifoundation.service.usage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;

@Slf4j
@Component
public class UsageStatisticsService {

    static final int WRITE_QUEUE_CAPACITY = 8_192;
    static final int MAX_WRITE_ATTEMPTS = 3;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private final UsageStatisticsStore store;
    private final CallerPluginResolver callerPluginResolver;
    private final Clock clock;
    private final ThreadPoolExecutor writer;
    private final ScheduledExecutorService maintenance;
    private final Scheduler readerScheduler;
    private final ReentrantReadWriteLock storeAccess = new ReentrantReadWriteLock(true);
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicLong incompleteCalls = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicReference<Instant> lastWriteErrorAt = new AtomicReference<>();
    private final AtomicReference<Instant> affectedSince = new AtomicReference<>();
    private final AtomicReference<Instant> affectedUntil = new AtomicReference<>();
    private final AtomicReference<String> migrationError = new AtomicReference<>();
    private final AtomicReference<String> integrityError = new AtomicReference<>();
    private final AtomicBoolean healthDirty = new AtomicBoolean();
    private volatile boolean available;
    private volatile boolean accepting;
    private volatile long epoch = 1;

    @Autowired
    public UsageStatisticsService(UsageStatisticsStore store,
        CallerPluginResolver callerPluginResolver) {
        this(store, callerPluginResolver, Clock.systemUTC());
    }

    UsageStatisticsService(UsageStatisticsStore store, CallerPluginResolver callerPluginResolver,
        Clock clock) {
        this.store = store;
        this.callerPluginResolver = callerPluginResolver;
        this.clock = clock;
        this.writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY), threadFactory("ai-usage-writer-"),
            new ThreadPoolExecutor.AbortPolicy());
        this.maintenance = Executors.newSingleThreadScheduledExecutor(
            threadFactory("ai-usage-maintenance-"));
        this.readerScheduler = Schedulers.newBoundedElastic(2, 128, "ai-usage-reader");
    }

    @PostConstruct
    public void initialize() {
        try {
            store.initialize();
            store.reconcileAbandoned(clock.instant());
            epoch = store.currentEpoch();
            restoreHealth(store.readHealth());
            available = true;
            accepting = true;
            maintenance.scheduleWithFixedDelay(this::enqueueMaintenance, 1, 24, TimeUnit.HOURS);
        } catch (RuntimeException error) {
            if (hasCause(error, UsageDatabaseIntegrityException.class)) {
                integrityError.set(safeMessage(error));
            } else {
                migrationError.set(safeMessage(error));
            }
            available = false;
            accepting = false;
            log.error("AI usage statistics are disabled because initialization failed", error);
        }
    }

    public UsageCallDescriptor describeCall(ModelCallContext context, String operation,
        boolean streaming, java.util.Map<String, Object> metadata) {
        var caller = callerPluginResolver.resolveCurrentCallerSnapshot();
        return new UsageCallDescriptor(context, operation, streaming,
            UsageFeature.fromMetadata(metadata), caller);
    }

    public UsageCallSession beginCall(UsageCallDescriptor descriptor) {
        var context = descriptor.context();
        var caller = descriptor.caller();
        var start = new UsageCallStart(java.util.UUID.randomUUID().toString(), epoch,
            clock.instant(), caller.getPluginName(), caller.getVersion(),
            caller.getDetectionSource(), descriptor.feature(), descriptor.operation(),
            context.modelType().name(), context.modelName(), context.providerName(),
            context.providerType(), context.modelId(), descriptor.streaming());
        var session = new UsageCallSession(this, start, clock);
        submit(() -> store.startCall(start), start.id(), () -> markIncomplete(session));
        return session;
    }

    void recordExecution(UsageCallSession session, UsageExecutionRecord execution) {
        submit(() -> store.recordExecution(execution), execution.callId(),
            () -> markIncomplete(session));
    }

    void finishCall(UsageCallSession session, UsageCallTerminal terminal) {
        submit(() -> store.finishCall(withCurrentCompleteness(session, terminal)),
            terminal.callId(), () -> markIncomplete(session));
    }

    public Mono<UsageSummary> summary(UsageQuery query) {
        return read(() -> store.summary(query, isComplete(query)));
    }

    public Mono<List<UsageTrendPoint>> trends(UsageQuery query) {
        return read(() -> store.trends(query, isComplete(query)));
    }

    public Mono<UsageCallPage> listCalls(UsageQuery query, int size, String cursor) {
        return read(() -> store.listCalls(query, size, cursor));
    }

    public Mono<Optional<UsageCallDetail>> getCall(String id) {
        return read(() -> store.getCall(id));
    }

    public Mono<Long> reset(String confirmation) {
        if (!"RESET".equals(confirmation)) {
            return Mono.error(new IllegalArgumentException("confirmation must equal RESET"));
        }
        return write(() -> {
            var nextEpoch = store.reset();
            epoch = nextEpoch;
            droppedEvents.set(0);
            incompleteCalls.set(0);
            writeFailures.set(0);
            lastWriteErrorAt.set(null);
            affectedSince.set(null);
            affectedUntil.set(null);
            migrationError.set(null);
            integrityError.set(null);
            healthDirty.set(false);
            return nextEpoch;
        });
    }

    public UsageHealth health() {
        return new UsageHealth(available, available && droppedEvents.get() == 0
            && incompleteCalls.get() == 0 && writeFailures.get() == 0
            && migrationError.get() == null && integrityError.get() == null,
            writer.getQueue().size(), droppedEvents.get(), incompleteCalls.get(),
            writeFailures.get(), lastWriteErrorAt.get(), affectedSince.get(),
            affectedUntil.get(),
            migrationError.get(), integrityError.get());
    }

    @PreDestroy
    public void close() {
        accepting = false;
        maintenance.shutdownNow();
        var maintenanceStopped = awaitMaintenanceTermination();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                var discarded = writer.shutdownNow().size();
                droppedEvents.addAndGet(discarded);
                markAffected();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            droppedEvents.addAndGet(writer.shutdownNow().size());
            markAffected();
        }
        if (!maintenanceStopped) {
            available = false;
            log.warn("Forcing the AI usage store closed to stop in-flight maintenance");
            store.close();
            awaitMaintenanceTermination();
            readerScheduler.dispose();
            return;
        }
        var lock = storeAccess.writeLock();
        var locked = false;
        try {
            locked = lock.tryLock(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                available = false;
                log.warn("AI usage store remained busy during shutdown; maintenance will close "
                    + "it after finishing");
                return;
            }
            if (available) {
                persistHealthIfDirty();
            }
            available = false;
            store.close();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            available = false;
        } finally {
            if (locked) {
                lock.unlock();
            }
            readerScheduler.dispose();
        }
    }

    private boolean awaitMaintenanceTermination() {
        try {
            if (!maintenance.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS)) {
                log.warn("AI usage maintenance did not stop within {}", SHUTDOWN_TIMEOUT);
                return false;
            }
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void submit(Runnable action, String callId) {
        submit(action, callId, () -> { });
    }

    private void submit(Runnable action, String callId, Runnable onPermanentFailure) {
        if (!accepting || !available) {
            droppedEvents.incrementAndGet();
            markAffected();
            onPermanentFailure.run();
            return;
        }
        try {
            writer.execute(() -> {
                RuntimeException failure = null;
                for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
                    try {
                        action.run();
                        persistHealthIfDirty();
                        return;
                    } catch (RuntimeException error) {
                        failure = error;
                        if (attempt < MAX_WRITE_ATTEMPTS) {
                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10L * attempt));
                        }
                    }
                }
                recordWriteFailure(failure);
                onPermanentFailure.run();
                persistHealthIfDirty();
                log.warn("Failed to persist AI usage statistics for call {}", callId, failure);
            });
        } catch (RejectedExecutionException error) {
            droppedEvents.incrementAndGet();
            markAffected();
            onPermanentFailure.run();
        }
    }

    private void markIncomplete(UsageCallSession session) {
        if (session.markIncomplete()) {
            incompleteCalls.incrementAndGet();
            markAffected(session.start().startedAt());
        }
    }

    private static UsageCallTerminal withCurrentCompleteness(UsageCallSession session,
        UsageCallTerminal terminal) {
        if (!session.isIncomplete() || !terminal.complete()) {
            return terminal;
        }
        return new UsageCallTerminal(terminal.start(), terminal.completedAt(), terminal.status(),
            terminal.error(), terminal.responseModelId(), terminal.stepCount(),
            terminal.attemptCount(), terminal.missingExecutionCount(), false, terminal.usage());
    }

    private <T> Mono<T> read(java.util.concurrent.Callable<T> query) {
        return access(query, storeAccess.readLock());
    }

    private <T> Mono<T> write(java.util.concurrent.Callable<T> query) {
        return access(query, storeAccess.writeLock());
    }

    private <T> Mono<T> access(java.util.concurrent.Callable<T> query, Lock lock) {
        return Mono.fromCallable(() -> {
            lock.lock();
            try {
                if (!available) {
                    throw new IllegalStateException("AI usage statistics are unavailable");
                }
                return query.call();
            } finally {
                lock.unlock();
            }
        }).subscribeOn(readerScheduler);
    }

    private void enqueueMaintenance() {
        var lock = storeAccess.readLock();
        lock.lock();
        try {
            if (!available) {
                return;
            }
            submit(() -> store.rollupAndRetain(clock), "maintenance-rollup");
            try {
                store.backup();
            } catch (RuntimeException error) {
                recordWriteFailure(error);
                persistHealthIfDirty();
                log.warn("Failed to back up AI usage statistics", error);
            }
        } finally {
            lock.unlock();
            if (!accepting && maintenance.isShutdown()) {
                store.close();
            }
        }
    }

    private void recordWriteFailure(Throwable error) {
        writeFailures.incrementAndGet();
        lastWriteErrorAt.set(clock.instant());
        markAffected();
        healthDirty.set(true);
    }

    private void markAffected() {
        markAffected(clock.instant());
    }

    private void markAffected(Instant affectedFrom) {
        var now = clock.instant();
        affectedSince.accumulateAndGet(affectedFrom,
            (current, candidate) -> current == null || candidate.isBefore(current)
                ? candidate : current);
        affectedUntil.accumulateAndGet(now,
            (current, candidate) -> current == null || candidate.isAfter(current)
                ? candidate : current);
        healthDirty.set(true);
    }

    private void restoreHealth(UsageHealthState health) {
        if (health == null) {
            return;
        }
        droppedEvents.set(health.droppedEvents());
        incompleteCalls.set(health.incompleteCalls());
        writeFailures.set(health.writeFailures());
        lastWriteErrorAt.set(health.lastWriteErrorAt());
        affectedSince.set(health.affectedSince());
        affectedUntil.set(health.affectedUntil());
        migrationError.set(health.migrationError());
        integrityError.set(health.integrityError());
    }

    private UsageHealthState healthState() {
        return new UsageHealthState(droppedEvents.get(), incompleteCalls.get(),
            writeFailures.get(), lastWriteErrorAt.get(), affectedSince.get(),
            affectedUntil.get(),
            migrationError.get(), integrityError.get());
    }

    private boolean isComplete(UsageQuery query) {
        var current = health();
        if (!current.available() || current.migrationError() != null
            || current.integrityError() != null) {
            return false;
        }
        if (current.complete()) {
            return true;
        }
        var since = current.affectedSince();
        var until = current.affectedUntil();
        if (since == null || until == null) {
            return false;
        }
        return !query.to().isAfter(since) || query.from().isAfter(until);
    }

    private void persistHealthIfDirty() {
        if (!healthDirty.compareAndSet(true, false)) {
            return;
        }
        try {
            store.writeHealth(healthState());
        } catch (RuntimeException error) {
            healthDirty.set(true);
            log.warn("Failed to persist AI usage statistics health", error);
        }
    }

    private static ThreadFactory threadFactory(String prefix) {
        var sequence = new AtomicLong();
        return runnable -> {
            var thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(UsageStatisticsService.class.getClassLoader());
            return thread;
        };
    }

    private static String safeMessage(Throwable error) {
        return error.getClass().getSimpleName();
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (var current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current == current.getCause()) {
                break;
            }
        }
        return false;
    }
}
