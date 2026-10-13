package run.halo.aifoundation.service.usage;

import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageUnitKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;

class UsageStatisticsServiceTest {

    private UsageStatisticsService service;

    @AfterEach
    void close() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void snapshotsCallerSynchronouslyAndAcceptsOnlyValidatedFeature() {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var callerResolver = mock(CallerPluginResolver.class);
        var caller = CallerPluginInfo.builder().detected(true).pluginName("plugin-search")
            .version("1.0.0").detectionSource("stack").build();
        when(callerResolver.resolveCurrentCallerSnapshot()).thenReturn(caller);
        service = new UsageStatisticsService(store, callerResolver);
        var context = new ModelCallContext(ModelType.LANGUAGE, "model", "provider", "openai",
            "gpt-4.1");

        var descriptor = service.describeCall(context, "language.generateText", false,
            Map.of("aifoundation.halo.run/feature", "semantic-search",
                "prompt", "must never be retained"));

        verify(callerResolver).resolveCurrentCallerSnapshot();
        assertThat(descriptor.caller()).isSameAs(caller);
        assertThat(descriptor.feature()).isEqualTo("semantic-search");
        assertThat(service.describeCall(context, "language.generateText", false,
            Map.of("aifoundation.halo.run/feature", "Invalid Feature!" )).feature()).isNull();
    }

    @Test
    void retriesTransientWriterFailureWithoutFailingStatisticsHealth() throws Exception {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        runWritesInBatch(store);
        doThrow(new IllegalStateException("database is busy"))
            .doThrow(new IllegalStateException("database is busy"))
            .doNothing().when(store).startCall(org.mockito.ArgumentMatchers.any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        service.beginCall(descriptor());

        await(() -> {
            try {
                verify(store, times(3)).startCall(org.mockito.ArgumentMatchers.any());
                return true;
            } catch (AssertionError error) {
                return false;
            }
        });
        assertThat(service.health().writeFailures()).isZero();
    }

    @Test
    void storageWideFailureDoesNotRetryEveryEventInTheBatch() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test barrier timed out");
            }
            throw new IllegalStateException("Storage unavailable",
                new java.sql.SQLException("Disk full", "", 13));
        }).when(store).writeBatch(org.mockito.ArgumentMatchers.anyList());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();
        try {
            service.beginCall(descriptor());
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 32; i++) {
                service.beginCall(descriptor());
            }
        } finally {
            release.countDown();
        }
        await(() -> service.health().droppedEvents() == 33);
        verify(store, times(2 * UsageStatisticsService.MAX_WRITE_ATTEMPTS))
            .writeBatch(org.mockito.ArgumentMatchers.anyList());
        assertThat(service.health().incompleteCalls()).isEqualTo(33);
    }

    @Test
    void reportsQueueSaturationWithoutBlockingModelThread()
        throws Exception {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        runWritesInBatch(store);
        var blocked = new CountDownLatch(1);
        var parked = new CountDownLatch(1);
        doAnswer(invocation -> {
            parked.countDown();
            blocked.await();
            return null;
        }).when(store).startCall(org.mockito.ArgumentMatchers.any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        try {
            // The writer takes up to one batch out of the queue before it blocks inside storage.
            // Park it first, then saturate the queue, so no enqueue race can free capacity.
            service.beginCall(descriptor());
            assertThat(parked.await(5, TimeUnit.SECONDS)).isTrue();

            var enqueueNanos = new long[UsageStatisticsService.WRITE_QUEUE_CAPACITY + 100];
            for (int i = 0; i < UsageStatisticsService.WRITE_QUEUE_CAPACITY + 100; i++) {
                var started = System.nanoTime();
                service.beginCall(descriptor());
                enqueueNanos[i] = System.nanoTime() - started;
            }
            java.util.Arrays.sort(enqueueNanos);
            var enqueueP95 =
                Duration.ofNanos(enqueueNanos[(int) Math.ceil(enqueueNanos.length * 0.95) - 1]);

            assertThat(service.health().droppedEvents()).isPositive();
            assertThat(enqueueP95).isLessThanOrEqualTo(Duration.ofMillis(1));
            System.out.println("AI usage enqueue p95: " + enqueueP95.toNanos() / 1_000D + " us");
        } finally {
            blocked.countDown();
        }
    }

    @Test
    void reportsPermanentWriterFailureAfterFiniteRetries() throws Exception {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        runWritesInBatch(store);
        doThrow(new IllegalStateException("database is busy"))
            .when(store).startCall(org.mockito.ArgumentMatchers.any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        service.beginCall(descriptor());

        await(() -> service.health().writeFailures() == 1);
        verify(store, times(UsageStatisticsService.MAX_WRITE_ATTEMPTS))
            .startCall(org.mockito.ArgumentMatchers.any());
        assertThat(service.health().complete()).isFalse();
        assertThat(service.health().affectedSince()).isNotNull();
    }

    @Test
    void permanentExecutionWriteFailureMarksThePersistedCallIncomplete() throws Exception {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        runWritesInBatch(store);
        doThrow(new IllegalStateException("database is busy"))
            .when(store).recordExecution(any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();
        var session = service.beginCall(descriptor());

        session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .succeed(new NormalizedUsage(2L, 1L, null, null, null, null, null, null), "model");
        session.succeed(NormalizedUsage.missing(), "model", 1);

        var terminal = ArgumentCaptor.forClass(UsageCallTerminal.class);
        await(() -> {
            try {
                verify(store).finishCall(terminal.capture());
                return true;
            } catch (AssertionError error) {
                return false;
            }
        });
        assertThat(terminal.getValue().complete()).isFalse();
        assertThat(service.health().incompleteCalls()).isEqualTo(1);
    }

    @Test
    void initializationFailureDisablesOnlyStatisticsAndSurfacesStorageHealth() {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        doThrow(new IllegalStateException("corrupt database")).when(store).initialize();
        service = new UsageStatisticsService(store, callerResolver());

        service.initialize();

        assertThat(service.health().available()).isFalse();
        assertThat(service.health().complete()).isFalse();
        assertThat(service.health().initializationError()).isEqualTo("IllegalStateException");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {11, 26, 267})
    void classifiesWrappedSqliteIntegrityFailuresWithoutExposingMessages(int code) {
        var store = mock(UsageStatisticsStore.class);
        doThrow(storageFailure(code)).when(store).initialize();
        service = new UsageStatisticsService(store, callerResolver());

        service.initialize();

        assertThat(service.health().available()).isFalse();
        assertThat(service.health().initializationError()).isNull();
        assertThat(service.health().integrityError())
            .isEqualTo("SQLException (SQLite errorCode=" + code + ")");
    }

    @Test
    void reportsRootInitializationCauseWithoutExposingMessagesOrSqlState() {
        var store = mock(UsageStatisticsStore.class);
        doThrow(storageFailure(14)).when(store).initialize();
        service = new UsageStatisticsService(store, callerResolver());

        service.initialize();

        assertThat(service.health().initializationError())
            .isEqualTo("SQLException (SQLite errorCode=14)");
        assertThat(service.health().integrityError()).isNull();
    }

    @Test
    void runtimeReadCorruptionDisablesFurtherStoreAccess() {
        var store = mock(UsageStatisticsStore.class);
        var query = query("2026-08-10T08:00:00Z", "2026-08-10T10:00:00Z");
        when(store.summary(query, true)).thenThrow(storageFailure(26));
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.summary(query).block())
            .isInstanceOf(IllegalStateException.class);

        assertThat(service.health().available()).isFalse();
        assertThat(service.health().integrityError())
            .isEqualTo("SQLException (SQLite errorCode=26)");
        assertThat(service.beginCall(descriptor()).isIncomplete()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.summary(query).block())
            .hasMessage("AI usage statistics are unavailable");
        verify(store).summary(query, true);
        verify(store, org.mockito.Mockito.never()).writeBatch(any());
    }

    @Test
    void runtimeWriteCorruptionStopsRetriesAndDiscardsQueuedEvents() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            throw storageFailure(11);
        }).when(store).writeBatch(any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        var first = service.beginCall(descriptor());
        UsageCallSession second;
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            second = service.beginCall(descriptor());
        } finally {
            release.countDown();
        }
        await(() -> service.health().droppedEvents() == 2);

        assertThat(first.isIncomplete()).isTrue();
        assertThat(second.isIncomplete()).isTrue();
        assertThat(service.health().available()).isFalse();
        assertThat(service.health().integrityError())
            .isEqualTo("SQLException (SQLite errorCode=11)");
        assertThat(service.health().queueDepth()).isZero();
        verify(store).writeBatch(any());
        verify(store, org.mockito.Mockito.never()).writeHealth(any());
    }

    @Test
    void readCorruptionDiscardsOnlyPendingEventsWhileInFlightBatchFinishes() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            invocation.getArgument(0, java.util.List.class).forEach(action ->
                ((Runnable) action).run());
            return null;
        }).when(store).writeBatch(any());
        var query = query("2026-08-10T08:00:00Z", "2026-08-10T10:00:00Z");
        when(store.summary(query, true)).thenThrow(storageFailure(11));
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        var first = service.beginCall(descriptor());
        UsageCallSession second;
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            second = service.beginCall(descriptor());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.summary(query).block())
                .isInstanceOf(IllegalStateException.class);
        } finally {
            release.countDown();
        }
        service.close();

        assertThat(first.isIncomplete()).isFalse();
        assertThat(second.isIncomplete()).isTrue();
        assertThat(service.health().droppedEvents()).isEqualTo(1);
        assertThat(service.health().writeFailures()).isZero();
        verify(store).writeBatch(any());
        verify(store).startCall(first.start());
        verify(store, org.mockito.Mockito.never()).startCall(second.start());
        verify(store, org.mockito.Mockito.never()).writeHealth(any());
    }

    @Test
    void runtimeMaintenanceIntegrityFailureDisablesStatistics() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.rollupAndRetainBatch(any())).thenThrow(new IllegalStateException(
            "private wrapper", new UsageDatabaseIntegrityException("private database path")));
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        invokeMaintenance(service);
        await(() -> service.health().writeFailures() == 1);

        assertThat(service.health().available()).isFalse();
        assertThat(service.health().integrityError())
            .isEqualTo("UsageDatabaseIntegrityException");
        invokeMaintenance(service);
        verify(store).rollupAndRetainBatch(any());
    }

    @Test
    void runtimeHealthWriteCorruptionDisablesStatistics() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        doThrow(new IllegalStateException("transient write failure"))
            .when(store).writeBatch(any());
        doThrow(storageFailure(11)).when(store).writeHealth(any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        service.beginCall(descriptor());
        await(() -> !service.health().available());

        assertThat(service.health().integrityError())
            .isEqualTo("SQLException (SQLite errorCode=11)");
        verify(store).writeHealth(any());
    }

    @Test
    void retriesWrappedSqliteBusyWithoutDisablingStatistics() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        doThrow(storageFailure(5)).doNothing().when(store).writeBatch(any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        service.beginCall(descriptor());
        service.close();

        verify(store, times(2)).writeBatch(any());
        assertThat(service.health().writeFailures()).isZero();
        assertThat(service.health().integrityError()).isNull();
    }

    private static IllegalStateException storageFailure(int code) {
        return new IllegalStateException("private wrapper", new java.sql.SQLException(
            "private database path or credential", "private SQLState", code));
    }

    @Test
    void restoresPersistedHealthDuringInitialization() {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        var affected = java.time.Instant.parse("2026-08-10T10:00:00Z");
        when(store.readHealth()).thenReturn(new UsageHealthState(3, 2, 1, affected, affected,
            affected, null, null));
        service = new UsageStatisticsService(store, callerResolver());

        service.initialize();

        assertThat(service.health().droppedEvents()).isEqualTo(3);
        assertThat(service.health().incompleteCalls()).isEqualTo(2);
        assertThat(service.health().writeFailures()).isEqualTo(1);
        assertThat(service.health().complete()).isFalse();
        assertThat(service.health().affectedSince()).isEqualTo(affected);
        assertThat(service.health().affectedUntil()).isEqualTo(affected);
    }

    @Test
    void marksOnlyQueriesThatIntersectPersistedAffectedIntervalIncomplete() {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        var since = Instant.parse("2026-08-10T10:00:00Z");
        var until = Instant.parse("2026-08-10T11:00:00Z");
        when(store.readHealth()).thenReturn(
            new UsageHealthState(1, 0, 0, null, since, until, null, null));
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();
        var before = query("2026-08-10T08:00:00Z", "2026-08-10T10:00:00Z");
        var overlapping = query("2026-08-10T09:00:00Z", "2026-08-10T10:30:00Z");
        var after = query("2026-08-10T11:00:01Z", "2026-08-10T12:00:00Z");

        service.summary(before).block();
        service.summary(overlapping).block();
        service.summary(after).block();

        verify(store).summary(before, true);
        verify(store).summary(overlapping, false);
        verify(store).summary(after, true);
    }

    @Test
    void resetClearsPersistedAndInMemoryStorageErrors() {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        when(store.readHealth()).thenReturn(new UsageHealthState(0, 0, 0, null, null, null,
            "initialization failed", "integrity check failed"));
        when(store.reset()).thenReturn(2L);
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        assertThat(service.health().complete()).isFalse();

        service.reset("RESET").block();
        verify(store).reset();
        assertThat(service.health().initializationError()).isNull();
        assertThat(service.health().integrityError()).isNull();
        assertThat(service.health().complete()).isTrue();
    }

    @Test
    void resetDiscardsQueuedAndLateOldEpochEventsWithoutAffectingNewHealth() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        runWritesInBatch(store);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return 2L;
        }).when(store).reset();
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        var reset = CompletableFuture.runAsync(() -> service.reset("RESET").block());
        UsageCallSession oldCall;
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            oldCall = service.beginCall(descriptor());
            for (int i = 0; i < UsageStatisticsService.WRITE_QUEUE_CAPACITY * 2; i++) {
                service.beginCall(descriptor());
            }
            assertThat(service.health().droppedEvents()).isPositive();
        } finally {
            release.countDown();
        }
        reset.get(3, TimeUnit.SECONDS);
        assertThat(service.health().queueDepth()).isZero();
        assertThat(service.health().complete()).isTrue();

        oldCall.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .succeed(NormalizedUsage.missing(), "gpt");
        oldCall.succeed(NormalizedUsage.missing(), "gpt", 1);
        var newCall = service.beginCall(descriptor());
        assertThat(newCall.start().epoch()).isEqualTo(2);
        service.close();

        verify(store).writeBatch(any());
        verify(store).startCall(newCall.start());
        verify(store, org.mockito.Mockito.never()).recordExecution(any());
        verify(store, org.mockito.Mockito.never()).finishCall(any());
        assertThat(service.health().droppedEvents()).isZero();
        assertThat(service.health().incompleteCalls()).isZero();
        assertThat(service.health().writeFailures()).isZero();
    }

    @Test
    void closesStore() {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        assertThatCode(service::close).doesNotThrowAnyException();

        verify(store).close();
        service = null;
    }

    @Test
    void waitsForAnInFlightResetBeforeClosingTheStore() throws Exception {
        var store = mock(UsageStatisticsStore.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        var resetStarted = new CountDownLatch(1);
        var releaseReset = new CountDownLatch(1);
        doAnswer(invocation -> {
            resetStarted.countDown();
            releaseReset.await();
            return 2L;
        }).when(store).reset();
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        var reset = CompletableFuture.supplyAsync(() -> service.reset("RESET").block());
        assertThat(resetStarted.await(2, TimeUnit.SECONDS)).isTrue();
        var close = CompletableFuture.runAsync(service::close);

        Thread.sleep(50);
        verify(store, org.mockito.Mockito.never()).close();
        releaseReset.countDown();
        assertThat(reset.get(2, TimeUnit.SECONDS)).isNull();
        close.get(2, TimeUnit.SECONDS);
        verify(store).close();
        service = null;
    }

    @Test
    void interruptsBlockedWriterBeforeTryingToPersistShutdownHealth(
        @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var paths = new UsageDatabasePaths(directory);
        var store = new SqliteUsageStatisticsStore(paths) {
            @Override
            public synchronized boolean rollupAndRetainBatch(java.time.Clock clock) {
                entered.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        // Model a native operation which does not honor a Java interruption.
                    }
                }
                return false;
            }

            @Override
            public void close() {
                release.countDown();
                super.close();
            }
        };
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();
        invokeMaintenance(service);
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        try {
            CompletableFuture.runAsync(service::close).get(7, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    @Test
    void drainsCallsBetweenMaintenanceBatches() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var nextBatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return true;
        }).doAnswer(invocation -> {
            verify(store).writeBatch(org.mockito.ArgumentMatchers.anyList());
            nextBatch.countDown();
            return false;
        }).when(store).rollupAndRetainBatch(any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();
        invokeMaintenance(service);
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        try {
            for (int i = 0; i < 100; i++) {
                service.beginCall(descriptor());
            }
        } finally {
            release.countDown();
        }
        assertThat(nextBatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(service.health().droppedEvents()).isZero();
    }

    private static CallerPluginResolver callerResolver() {
        var resolver = mock(CallerPluginResolver.class);
        when(resolver.resolveCurrentCallerSnapshot()).thenReturn(CallerPluginInfo.builder()
            .pluginName("plugin").version("1").detectionSource("stack").build());
        return resolver;
    }

    // UsageStatisticsStore.writeBatch is abstract; replicate the former default
    // implementation for CALLS_REAL_METHODS mocks.
    private static void runWritesInBatch(UsageStatisticsStore store) {
        doAnswer(invocation -> {
            invocation.getArgument(0, java.util.List.class).forEach(action ->
                ((Runnable) action).run());
            return null;
        }).when(store).writeBatch(org.mockito.ArgumentMatchers.anyList());
    }

    private static UsageCallDescriptor descriptor() {
        return new UsageCallDescriptor(new ModelCallContext(ModelType.LANGUAGE, "model",
            "provider", "openai", "gpt"), "language.generateText", false, null,
            CallerPluginInfo.builder().pluginName("plugin").version("1")
                .detectionSource("stack").build());
    }

    private static UsageQuery query(String from, String to) {
        return new UsageQuery(Instant.parse(from), Instant.parse(to), null, null, null, null,
            null, null, null, null, null);
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        var deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void invokeMaintenance(UsageStatisticsService service) {
        try {
            Method method = UsageStatisticsService.class.getDeclaredMethod("enqueueMaintenance");
            method.setAccessible(true);
            method.invoke(service);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

}
