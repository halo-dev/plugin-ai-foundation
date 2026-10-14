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
public class UsageStatisticsService
    implements run.halo.aifoundation.service.observation.UsageObservation {

    static final int WRITE_QUEUE_CAPACITY = 8_192;
    static final int MAX_WRITE_ATTEMPTS = 3;
    private static final Duration CALL_DETAIL_RANGE = Duration.ofDays(90);
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
    private final java.util.Map<String, TrackedCall> trackedCalls = new java.util.HashMap<>();
    private boolean reconciliationNeeded;
    private final ScheduledExecutorService maintenance;
    private final Scheduler readerScheduler;
    private final ReentrantReadWriteLock storeAccess = new ReentrantReadWriteLock(true);
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicLong incompleteCalls = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicReference<Instant> lastWriteErrorAt = new AtomicReference<>();
    private final AtomicReference<Instant> affectedSince = new AtomicReference<>();
    private final AtomicReference<Instant> affectedUntil = new AtomicReference<>();
    private final AtomicReference<String> initializationError = new AtomicReference<>();
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
            if (isIntegrityFailure(error)) {
                integrityError.set(safeMessage(error));
            } else {
                initializationError.set(safeMessage(error));
            }
            available = false;
            accepting = false;
            log.error("AI usage statistics are disabled because initialization failed ({})",
                safeMessage(error), error);
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
        synchronized (admissionLock) {
            if (start.epoch() != epoch) {
                return null;
            }
            if (trackedCalls.size() >= WRITE_QUEUE_CAPACITY) {
                droppedEvents.incrementAndGet();
                markIncomplete(session);
                return null;
            }
            trackedCalls.put(start.id(), new TrackedCall(session, false));
            submit(start.epoch(), () -> store.startCall(start), () -> markIncomplete(session));
        }
        return session;
    }

    public void recordExecution(UsageCallSession session, UsageExecutionRecord execution) {
        submit(session.start().epoch(), () -> store.recordExecution(execution),
            () -> markIncomplete(session));
    }

    public void finishCall(UsageCallSession session, UsageCallTerminal terminal) {
        synchronized (admissionLock) {
            if (session.start().epoch() != epoch) {
                return;
            }
            if (!terminal.complete()) {
                markIncomplete(session);
            }
        }
        submit(session.start().epoch(), () -> {
            store.finishCall(withCurrentCompleteness(session, terminal));
            synchronized (admissionLock) {
                trackedCalls.computeIfPresent(session.start().id(),
                    (id, tracked) -> new TrackedCall(tracked.session(), true));
            }
        }, () -> {
            synchronized (admissionLock) {
                if (session.start().epoch() != epoch) {
                    return;
                }
                markIncomplete(session);
                trackedCalls.computeIfPresent(session.start().id(),
                    (id, tracked) -> new TrackedCall(tracked.session(), true));
                reconciliationNeeded = true;
            }
        });
    }

    public Mono<UsageSummary> summary(UsageQuery query) {
        return read(() -> store.summary(query, isComplete(query)));
    }

    public Mono<List<UsageTrendPoint>> trends(UsageQuery query,
        UsageBreakdownDimension dimension) {
        return read(() -> store.trends(query, isComplete(query), dimension));
    }

    public Mono<UsageBreakdown> breakdown(UsageQuery query, UsageBreakdownDimension dimension) {
        return read(() -> store.breakdown(query, dimension));
    }

    public Mono<UsageCallPage> listCalls(UsageQuery query, int size, String cursor) {
        return read(() -> {
            var cutoff = clock.instant().minus(CALL_DETAIL_RANGE);
            if (!query.to().isAfter(cutoff)) {
                return new UsageCallPage(List.of(), null);
            }
            var recent = query.from().isBefore(cutoff)
                ? new UsageQuery(cutoff, query.to(), query.callerPlugin(), query.feature(),
                    query.providerName(), query.modelName(), query.modelType(), query.operation(),
                    query.status(), query.usageQuality(), query.resolution(), query.timeZone())
                : query;
            var recentCursor = cursor;
            if (cursor != null && !cursor.isBlank()) {
                var decoded = UsageCursor.decode(cursor, query);
                recentCursor = UsageCursor.encode(decoded.startedAt(), decoded.id(), recent);
            }
            var page = store.listCalls(recent, size, recentCursor);
            if (page.nextCursor() == null) {
                return page;
            }
            var next = UsageCursor.decode(page.nextCursor(), recent);
            return new UsageCallPage(page.items(),
                UsageCursor.encode(next.startedAt(), next.id(), query));
        });
    }

    public Mono<Optional<UsageCallDetail>> getCall(String id) {
        return read(() -> store.getCall(id).filter(detail ->
            !detail.call().startedAt().isBefore(clock.instant().minus(CALL_DETAIL_RANGE))));
    }

    public Mono<Void> reset(String confirmation) {
        if (!"RESET".equals(confirmation)) {
            return Mono.error(new IllegalArgumentException("confirmation must equal RESET"));
        }
        return write(() -> {
            var nextEpoch = store.reset();
            synchronized (admissionLock) {
                droppedEvents.set(0);
                incompleteCalls.set(0);
                writeFailures.set(0);
                lastWriteErrorAt.set(null);
                affectedSince.set(null);
                affectedUntil.set(null);
                initializationError.set(null);
                integrityError.set(null);
                healthDirty.set(false);
                // Reset retires queued events as well as persisted history.
                pendingWrites.clear();
                trackedCalls.clear();
                reconciliationNeeded = false;
                epoch = nextEpoch;
            }
            return null;
        });
    }

    public UsageHealth health() {
        return new UsageHealth(available, hasCompleteHistory(),
            pendingWrites.size(), droppedEvents.get(), incompleteCalls.get(),
            writeFailures.get(), lastWriteErrorAt.get(), affectedSince.get(),
            affectedUntil.get(),
            initializationError.get(), integrityError.get());
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
        if (initializationError.get() != null) {
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
        readerScheduler.dispose();
    }

    private void submit(long eventEpoch, Runnable action, Runnable onPermanentFailure) {
        synchronized (admissionLock) {
            if (eventEpoch != epoch) {
                return;
            }
            if (!enqueueWrite(new PendingWrite(eventEpoch, action, onPermanentFailure))) {
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
        synchronized (admissionLock) {
            // Close owns the final drain once admission is closed. Checking and scheduling
            // under the same lock prevents a yielded drain from racing executor shutdown.
            if (closing.get() || !draining.compareAndSet(false, true)) {
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
                    batch.removeIf(event -> event.epoch() != epoch);
                    if (batch.isEmpty()) {
                        continue;
                    }
                    if (!available) {
                        for (var event : batch) {
                            droppedEvents.incrementAndGet();
                            event.onFailure().run();
                        }
                        discardPendingWrites();
                        break;
                    }
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
            reconcileLostTerminals();
            draining.set(false);
            rescheduleDrain();
        }
    }

    private void reconcileLostTerminals() {
        var lock = storeAccess.readLock();
        lock.lock();
        try {
            if (!persistHealthIfDirty()) {
                return;
            }
            final java.util.Set<String> active;
            synchronized (admissionLock) {
                if (!available || healthDirty.get() || !reconciliationNeeded
                    || !pendingWrites.isEmpty()) {
                    return;
                }
                // Only this writer persists queued events. New admissions after the snapshot
                // cannot reach SQLite until this recovery finishes; reset holds the write lock.
                active = trackedCalls.entrySet().stream()
                    .filter(entry -> !entry.getValue().ended())
                    .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
                reconciliationNeeded = false;
            }
            try {
                store.reconcileAbandoned(clock.instant(), active);
            } catch (RuntimeException | LinkageError error) {
                synchronized (admissionLock) {
                    reconciliationNeeded = true;
                }
                recordWriteFailure(error);
            }
            persistHealthIfDirty();
        } finally {
            lock.unlock();
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
        if (!available || failure instanceof LinkageError || isIntegrityFailure(failure)) {
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
            if (!available || failure instanceof LinkageError || isIntegrityFailure(failure)) {
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
            if (!available) {
                return failure == null
                    ? new IllegalStateException("AI usage statistics are unavailable") : failure;
            }
            try {
                store.writeBatch(batch.stream().map(PendingWrite::action).toList());
                return null;
            } catch (LinkageError error) {
                return error;
            } catch (RuntimeException error) {
                failure = error;
                if (isIntegrityFailure(error) || !available) {
                    return error;
                }
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

    private record PendingWrite(long epoch, Runnable action, Runnable onFailure) {
    }

    private record TrackedCall(UsageCallSession session, boolean ended) {
    }

    private void markIncomplete(UsageCallSession session) {
        synchronized (admissionLock) {
            if (session.start().epoch() == epoch && session.markIncomplete()) {
                incompleteCalls.incrementAndGet();
                markAffected(session.start().startedAt());
            }
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
            } catch (Exception | LinkageError error) {
                disableIfCorrupt(error);
                throw error;
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
        synchronized (admissionLock) {
            if (!canAccessStore()) {
                return;
            }
            if (!maintenanceQueued.compareAndSet(false, true)) {
                return;
            }
            writer.execute(this::maintainOneBatch);
        }
    }

    private void maintainOneBatch() {
        reconcileLostTerminals();
        var more = false;
        var lock = storeAccess.readLock();
        lock.lock();
        try {
            if (canAccessStore()) {
                more = store.retainBatch(clock);
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
        disableIfCorrupt(error);
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
        initializationError.set(health.initializationError());
        integrityError.set(health.integrityError());
    }

    private UsageHealthState healthState() {
        return new UsageHealthState(droppedEvents.get(), incompleteCalls.get(),
            writeFailures.get(), lastWriteErrorAt.get(), affectedSince.get(),
            affectedUntil.get(),
            initializationError.get(), integrityError.get());
    }

    private boolean isComplete(UsageQuery query) {
        var current = health();
        if (!current.available()) {
            return false;
        }
        if (current.initializationError() != null) {
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

    private boolean persistHealthIfDirty() {
        final UsageHealthState health;
        final List<UsageCallStart> incomplete;
        final java.util.Map<String, TrackedCall> ended;
        synchronized (admissionLock) {
            if (!available) {
                return false;
            }
            ended = trackedCalls.entrySet().stream().filter(entry -> entry.getValue().ended())
                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                    java.util.Map.Entry::getValue));
            if (!healthDirty.compareAndSet(true, false)) {
                ended.forEach((id, tracked) -> trackedCalls.remove(id, tracked));
                return true;
            }
            health = healthState();
            incomplete = trackedCalls.values().stream().map(TrackedCall::session)
                .filter(UsageCallSession::isIncomplete).map(UsageCallSession::start).toList();
        }
        try {
            store.writeHealth(health, incomplete);
            synchronized (admissionLock) {
                if (!healthDirty.get()) {
                    ended.forEach((id, tracked) -> trackedCalls.remove(id, tracked));
                }
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            disableIfCorrupt(error);
            healthDirty.set(true);
            log.warn("Failed to persist AI usage statistics health");
            return false;
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

    private void disableIfCorrupt(Throwable error) {
        if (!isIntegrityFailure(error)) {
            return;
        }
        synchronized (admissionLock) {
            integrityError.compareAndSet(null, safeMessage(error));
            accepting = false;
            available = false;
            markAffected();
            discardPendingWrites();
        }
    }

    private static String safeMessage(Throwable error) {
        var root = error;
        SQLException sql = null;
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            root = error;
            if (error instanceof SQLException cause) {
                sql = cause;
            }
        }
        return root.getClass().getSimpleName()
            + (sql == null ? "" : " (SQLite errorCode=" + sql.getErrorCode() + ")");
    }

    private static boolean isIntegrityFailure(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof UsageDatabaseIntegrityException) {
                return true;
            }
            if (error instanceof SQLException sql) {
                var code = sql.getErrorCode() & 0xff;
                if (code == 11 || code == 26) {
                    return true;
                }
            }
        }
        return false;
    }
}
