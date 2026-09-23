package run.halo.aifoundation.service.usage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageFeature;

@Slf4j
@Component
public class UsageStatisticsService implements run.halo.aifoundation.service.observation.UsageObservation,
    run.halo.aifoundation.service.observation.UsageEventSink {

    static final int WRITE_QUEUE_CAPACITY = 8_192;
    static final int MAX_WRITE_ATTEMPTS = 3;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private final UsageStatisticsStore store;
    private final CallerPluginResolver callerPluginResolver;
    private final Clock clock;
    private final ThreadPoolExecutor writer;
    private final ArrayBlockingQueue<PendingWrite> pendingWrites =
        new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY);
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Object admissionLock = new Object();
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
    private final AtomicBoolean maintenanceQueued = new AtomicBoolean();

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
        } catch (RuntimeException | LinkageError error) {
            if (hasCause(error, UsageDatabaseIntegrityException.class)) {
                integrityError.set(safeMessage(error));
            } else {
                migrationError.set(safeMessage(error));
            }
            available = false;
            accepting = false;
            log.error("AI usage statistics are disabled because initialization failed ({})",
                error.getClass().getSimpleName());
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
        submit(() -> store.startCall(start), () -> markIncomplete(session));
        return session;
    }

    public void recordExecution(UsageCallSession session, UsageExecutionRecord execution) {
        submit(() -> store.recordExecution(execution),
            () -> markIncomplete(session));
    }

    public void finishCall(UsageCallSession session, UsageCallTerminal terminal) {
        submit(() -> store.finishCall(withCurrentCompleteness(session, terminal)),
            () -> markIncomplete(session));
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
        return new UsageHealth(available, hasCompleteHistory(),
            pendingWrites.size(), droppedEvents.get(), incompleteCalls.get(),
            writeFailures.get(), lastWriteErrorAt.get(), affectedSince.get(),
            affectedUntil.get(),
            migrationError.get(), integrityError.get());
    }

    private boolean hasCompleteHistory() {
        if (!available) {
            return false;
        }
        if (droppedEvents.get() != 0) {
            return false;
        }
        if (incompleteCalls.get() != 0) {
            return false;
        }
        if (writeFailures.get() != 0) {
            return false;
        }
        if (migrationError.get() != null) {
            return false;
        }
        return integrityError.get() == null;
    }

    @PreDestroy
    public void close() {
        synchronized (admissionLock) {
            if (!closing.compareAndSet(false, true)) {
                return;
            }
            accepting = false;
        }
        maintenance.shutdownNow();
        var maintenanceStopped = awaitMaintenanceTermination();
        // A final task handles a drain that yielded just before shutdown began.
        // All accepted events were enqueued before admission was closed.
        writer.execute(this::drainWrites);
        writer.shutdown();
        var writerStopped = false;
        try {
            writerStopped = writer.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!writerStopped) {
                writer.shutdownNow();
                discardPendingWrites();
                markAffected();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
            discardPendingWrites();
            markAffected();
        }
        if (!maintenanceStopped) {
            forceCloseStore();
            return;
        }
        if (!writerStopped) {
            forceCloseStore();
            return;
        }
        var lock = storeAccess.writeLock();
        var locked = false;
        try {
            locked = lock.tryLock(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                available = false;
                log.warn("Closing the AI usage store to interrupt outstanding readers");
                store.close();
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
            store.close();
        } finally {
            if (locked) {
                lock.unlock();
            }
            readerScheduler.dispose();
        }
    }

    private void forceCloseStore() {
        available = false;
        log.warn("Forcing the AI usage store closed to interrupt outstanding work");
        store.close();
        awaitMaintenanceTermination();
        readerScheduler.dispose();
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

    private void submit(Runnable action, Runnable onPermanentFailure) {
        synchronized (admissionLock) {
            if (!enqueueWrite(new PendingWrite(action, onPermanentFailure))) {
                droppedEvents.incrementAndGet();
                markAffected();
                onPermanentFailure.run();
                return;
            }
            scheduleDrain();
        }
    }

    private boolean enqueueWrite(PendingWrite event) {
        if (!accepting) {
            return false;
        }
        if (!available) {
            return false;
        }
        return pendingWrites.offer(event);
    }

    private void discardPendingWrites() {
        PendingWrite event;
        while ((event = pendingWrites.poll()) != null) {
            droppedEvents.incrementAndGet();
            event.onFailure().run();
        }
    }

    private void scheduleDrain() {
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        try {
            writer.execute(this::drainWrites);
        } catch (RejectedExecutionException error) {
            draining.set(false);
            PendingWrite event;
            while ((event = pendingWrites.poll()) != null) {
                droppedEvents.incrementAndGet();
                event.onFailure().run();
            }
            markAffected();
        }
    }

    private void drainWrites() {
        try {
            var batch = new java.util.ArrayList<PendingWrite>(128);
            // Yield periodically so queued maintenance is not starved by continuous traffic.
            for (int batches = 0; !Thread.currentThread().isInterrupted(); batches++) {
                if (shouldYieldWriter(batches)) {
                    break;
                }
                if (pendingWrites.drainTo(batch, 128) == 0) {
                    break;
                }
                var lock = storeAccess.readLock();
                lock.lock();
                try {
                    var failure = writeBatchWithRetries(batch);
                    handleBatchFailure(batch, failure);
                    if (failure != null) {
                        log.warn("AI usage batch failed ({})", failure.getClass().getSimpleName());
                    }
                    persistHealthIfDirty();
                } finally {
                    lock.unlock();
                }
                batch.clear();
            }
        } finally {
            draining.set(false);
            rescheduleDrain();
        }
    }

    private boolean shouldYieldWriter(int batches) {
        if (closing.get()) {
            return false;
        }
        return batches >= 8;
    }

    private void rescheduleDrain() {
        if (closing.get()) {
            return;
        }
        if (pendingWrites.isEmpty()) {
            return;
        }
        scheduleDrain();
    }

    private void handleBatchFailure(List<PendingWrite> batch, Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof LinkageError) {
            accepting = false;
            available = false;
            batch.forEach(event -> failedWrite(event, failure));
            discardPendingWrites();
            return;
        }
        if (isStorageFailure(failure)) {
            batch.forEach(event -> failedWrite(event, failure));
            return;
        }
        if (batch.size() == 1) {
            failedWrite(batch.getFirst(), failure);
            return;
        }
        isolateFailedEvents(batch);
    }

    private void isolateFailedEvents(List<PendingWrite> batch) {
        // Retry individual events only after rollback. A poison event must not hide later terminals.
        for (int index = 0; index < batch.size(); index++) {
            var failure = writeBatchWithRetries(List.of(batch.get(index)));
            if (failure == null) {
                continue;
            }
            if (failure instanceof LinkageError) {
                failRemainingEvents(batch, index, failure);
                discardPendingWrites();
                return;
            }
            if (isStorageFailure(failure)) {
                failRemainingEvents(batch, index, failure);
                return;
            }
            failedWrite(batch.get(index), failure);
        }
    }

    private void failRemainingEvents(List<PendingWrite> batch, int from, Throwable failure) {
        for (var event : batch.subList(from, batch.size())) {
            failedWrite(event, failure);
        }
    }

    private Throwable writeBatchWithRetries(List<PendingWrite> batch) {
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                store.writeBatch(batch.stream().map(PendingWrite::action).toList());
                return null;
            } catch (LinkageError error) {
                return error;
            } catch (RuntimeException error) {
                failure = error;
                if (attempt < MAX_WRITE_ATTEMPTS) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10L * attempt));
                }
            }
        }
        return failure;
    }

    private void failedWrite(PendingWrite event, Throwable failure) {
        recordWriteFailure(failure);
        event.onFailure().run();
        droppedEvents.incrementAndGet();
    }

    private static boolean isStorageFailure(Throwable error) {
        for (int depth = 0; depth < 8; depth++, error = error.getCause()) {
            if (error == null) {
                return false;
            }
            if (error instanceof SQLException sql) {
                // SQLite primary result codes: read-only, busy/locked, I/O, full, cannot open,
                // corrupt/not-a-database. Retrying each event cannot isolate these failures.
                var storageFailure = switch (sql.getErrorCode() & 0xff) {
                    case 5, 6, 8, 10, 11, 13, 14, 26 -> true;
                    default -> false;
                };
                if (storageFailure) {
                    return true;
                }
            }
        }
        return false;
    }

    private record PendingWrite(Runnable action, Runnable onFailure) {
    }

    private void markIncomplete(UsageCallSession session) {
        if (session.markIncomplete()) {
            incompleteCalls.incrementAndGet();
            markAffected(session.start().startedAt());
        }
    }

    private static UsageCallTerminal withCurrentCompleteness(UsageCallSession session,
        UsageCallTerminal terminal) {
        if (!session.isIncomplete()) {
            return terminal;
        }
        if (!terminal.complete()) {
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
                if (!canAccessStore()) {
                    throw new IllegalStateException("AI usage statistics are unavailable");
                }
                return query.call();
            } finally {
                lock.unlock();
            }
        }).subscribeOn(readerScheduler);
    }

    private boolean canAccessStore() {
        if (!available) {
            return false;
        }
        return !closing.get();
    }

    private void enqueueMaintenance() {
        var lock = storeAccess.readLock();
        lock.lock();
        try {
            if (!available) {
                return;
            }
            synchronized (admissionLock) {
                if (closing.get()) {
                    return;
                }
                if (!maintenanceQueued.compareAndSet(false, true)) {
                    return;
                }
                writer.execute(this::maintainOneBatch);
            }
            try {
                store.backup();
            } catch (RuntimeException | LinkageError error) {
                recordWriteFailure(error);
                persistHealthIfDirty();
                log.warn("Failed to back up AI usage statistics");
            }
        } finally {
            lock.unlock();
        }
    }

    private void maintainOneBatch() {
        var more = false;
        var lock = storeAccess.readLock();
        lock.lock();
        try {
            if (canAccessStore()) {
                more = store.rollupAndRetainBatch(clock);
            }
        } catch (RuntimeException | LinkageError error) {
            recordWriteFailure(error);
        } finally {
            lock.unlock();
        }
        synchronized (admissionLock) {
            if (!more) {
                maintenanceQueued.set(false);
                return;
            }
            if (!canAccessStore()) {
                maintenanceQueued.set(false);
                return;
            }
            // Enqueue behind any pending drain. Never keep the writer for a full retention run.
            writer.execute(this::maintainOneBatch);
        }
    }

    private void recordWriteFailure(Throwable error) {
        if (error instanceof LinkageError) {
            accepting = false;
            available = false;
        }
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
        affectedSince.accumulateAndGet(affectedFrom, UsageStatisticsService::earliest);
        affectedUntil.accumulateAndGet(now, UsageStatisticsService::latest);
        healthDirty.set(true);
    }

    private static Instant earliest(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate.isBefore(current) ? candidate : current;
    }

    private static Instant latest(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate.isAfter(current) ? candidate : current;
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
        if (!current.available()) {
            return false;
        }
        if (current.migrationError() != null) {
            return false;
        }
        if (current.integrityError() != null) {
            return false;
        }
        if (current.complete()) {
            return true;
        }
        var since = current.affectedSince();
        var until = current.affectedUntil();
        if (since == null) {
            return false;
        }
        if (until == null) {
            return false;
        }
        if (!query.to().isAfter(since)) {
            return true;
        }
        return query.from().isAfter(until);
    }

    private void persistHealthIfDirty() {
        if (!healthDirty.compareAndSet(true, false)) {
            return;
        }
        try {
            store.writeHealth(healthState());
        } catch (RuntimeException | LinkageError error) {
            healthDirty.set(true);
            log.warn("Failed to persist AI usage statistics health");
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
