package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageFeature;

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
        return new UsageHealth(available, available && droppedEvents.get() == 0
            && incompleteCalls.get() == 0 && writeFailures.get() == 0
            && migrationError.get() == null && integrityError.get() == null,
            pendingWrites.size(), droppedEvents.get(), incompleteCalls.get(),
            writeFailures.get(), lastWriteErrorAt.get(), affectedSince.get(),
            affectedUntil.get(),
            migrationError.get(), integrityError.get());
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
        if (!maintenanceStopped || !writerStopped) {
            available = false;
            log.warn("Forcing the AI usage store closed to interrupt outstanding work");
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
            if (!accepting || !available
                || !pendingWrites.offer(new PendingWrite(action, onPermanentFailure))) {
                droppedEvents.incrementAndGet();
                markAffected();
                onPermanentFailure.run();
                return;
            }
            scheduleDrain();
        }
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
            for (int batches = 0; (batches < 8 || closing.get())
                && !Thread.currentThread().isInterrupted()
                && pendingWrites.drainTo(batch, 128) > 0; batches++) {
                var lock = storeAccess.readLock();
                lock.lock();
                try {
                    var failure = writeBatchWithRetries(batch);
                    if (failure instanceof LinkageError) {
                        accepting = false;
                        available = false;
                        batch.forEach(event -> failedWrite(event, failure));
                        discardPendingWrites();
                    } else if (failure != null && !isStorageFailure(failure) && batch.size() > 1) {
                        // Isolate a poison event after rolling back the microbatch so later
                        // terminal events can still persist with complete=false.
                        for (int index = 0; index < batch.size(); index++) {
                            var eventFailure = writeBatchWithRetries(List.of(batch.get(index)));
                            if (eventFailure instanceof LinkageError || isStorageFailure(eventFailure)) {
                                for (var remaining : batch.subList(index, batch.size())) {
                                    failedWrite(remaining, eventFailure);
                                }
                                if (eventFailure instanceof LinkageError) {
                                    discardPendingWrites();
                                }
                                break;
                            }
                            if (eventFailure != null) {
                                failedWrite(batch.get(index), eventFailure);
                            }
                        }
                    } else if (failure != null) {
                        batch.forEach(event -> failedWrite(event, failure));
                    }
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
            if (!closing.get() && !pendingWrites.isEmpty()) {
                scheduleDrain();
            }
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
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof java.sql.SQLException sql) {
                // SQLite primary result codes: read-only, busy/locked, I/O, full, cannot open,
                // corrupt/not-a-database. Retrying each event cannot isolate these failures.
                var code = sql.getErrorCode() & 0xff;
                if (code == 5 || code == 6 || code == 8 || code == 10 || code == 11
                    || code == 13 || code == 14 || code == 26) {
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
                if (!available || closing.get()) {
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
            synchronized (admissionLock) {
                if (closing.get() || !maintenanceQueued.compareAndSet(false, true)) {
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
            if (available && !closing.get()) {
                more = store.rollupAndRetainBatch(clock);
            }
        } catch (RuntimeException | LinkageError error) {
            recordWriteFailure(error);
        } finally {
            lock.unlock();
        }
        synchronized (admissionLock) {
            if (more && available && !closing.get()) {
                // Enqueue behind any pending drain. Never keep the writer for a full retention run.
                writer.execute(this::maintainOneBatch);
            } else {
                maintenanceQueued.set(false);
            }
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
