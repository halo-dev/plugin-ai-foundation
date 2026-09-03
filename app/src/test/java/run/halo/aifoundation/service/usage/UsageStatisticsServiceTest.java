package run.halo.aifoundation.service.usage;

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
        var store = mock(UsageStatisticsStore.class);
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
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
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
    void reportsQueueSaturationWithoutBlockingModelThread()
        throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var blocked = new CountDownLatch(1);
        doAnswer(invocation -> {
            blocked.await();
            return null;
        }).when(store).startCall(org.mockito.ArgumentMatchers.any());
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        var enqueueNanos = new long[UsageStatisticsService.WRITE_QUEUE_CAPACITY + 100];
        for (int i = 0; i < UsageStatisticsService.WRITE_QUEUE_CAPACITY + 100; i++) {
            var started = System.nanoTime();
            service.beginCall(descriptor());
            enqueueNanos[i] = System.nanoTime() - started;
        }
        java.util.Arrays.sort(enqueueNanos);
        var enqueueP95 = Duration.ofNanos(enqueueNanos[(int) Math.ceil(enqueueNanos.length * 0.95)
            - 1]);

        assertThat(service.health().droppedEvents()).isPositive();
        assertThat(enqueueP95).isLessThanOrEqualTo(Duration.ofMillis(1));
        System.out.println("AI usage enqueue p95: " + enqueueP95.toNanos() / 1_000D + " us");
        blocked.countDown();
    }

    @Test
    void reportsPermanentWriterFailureAfterFiniteRetries() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
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
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
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
    void initializationFailureDisablesOnlyStatisticsAndSurfacesRecoveryHealth() {
        var store = mock(UsageStatisticsStore.class);
        doThrow(new IllegalStateException("corrupt database")).when(store).initialize();
        service = new UsageStatisticsService(store, callerResolver());

        service.initialize();

        assertThat(service.health().available()).isFalse();
        assertThat(service.health().complete()).isFalse();
        assertThat(service.health().migrationError()).isEqualTo("IllegalStateException");
    }

    @Test
    void restoresPersistedHealthDuringInitialization() {
        var store = mock(UsageStatisticsStore.class);
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
        var store = mock(UsageStatisticsStore.class);
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
    void resetClearsPersistedAndInMemoryRecoveryErrors() {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        when(store.readHealth()).thenReturn(new UsageHealthState(0, 0, 0, null, null, null,
            "migration failed", "recovered from corrupt database"));
        when(store.reset()).thenReturn(2L);
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        assertThat(service.health().complete()).isFalse();

        assertThat(service.reset("RESET").block()).isEqualTo(2L);
        assertThat(service.health().migrationError()).isNull();
        assertThat(service.health().integrityError()).isNull();
        assertThat(service.health().complete()).isTrue();
    }

    @Test
    void closesStoreWithoutRunningAnUnboundedShutdownBackup() {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        assertThatCode(service::close).doesNotThrowAnyException();

        verify(store).close();
        verify(store, org.mockito.Mockito.never()).backup();
        service = null;
    }

    @Test
    void closesStoreToInterruptMaintenanceThatIgnoresThreadInterruption() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var backupStarted = new CountDownLatch(1);
        var releaseBackup = new CountDownLatch(1);
        doAnswer(invocation -> {
            backupStarted.countDown();
            while (releaseBackup.getCount() > 0) {
                try {
                    releaseBackup.await();
                } catch (InterruptedException ignored) {
                    // Simulate a JDBC operation that does not respond to thread interruption.
                }
            }
            return null;
        }).when(store).backup();
        doAnswer(invocation -> {
            releaseBackup.countDown();
            return null;
        }).when(store).close();
        service = new UsageStatisticsService(store, callerResolver());
        service.initialize();

        var maintenance = maintenanceExecutor(service);
        maintenance.execute(() -> invokeMaintenance(service));
        assertThat(backupStarted.await(2, TimeUnit.SECONDS)).isTrue();

        var close = CompletableFuture.runAsync(service::close);
        close.get(7, TimeUnit.SECONDS);
        assertThat(maintenance.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        verify(store, org.mockito.Mockito.atLeastOnce()).close();
        service = null;
    }

    @Test
    void waitsForAnInFlightResetBeforeClosingTheStore() throws Exception {
        var store = mock(UsageStatisticsStore.class);
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
        assertThat(reset.get(2, TimeUnit.SECONDS)).isEqualTo(2L);
        close.get(2, TimeUnit.SECONDS);
        verify(store).close();
        service = null;
    }

    private static CallerPluginResolver callerResolver() {
        var resolver = mock(CallerPluginResolver.class);
        when(resolver.resolveCurrentCallerSnapshot()).thenReturn(CallerPluginInfo.builder()
            .pluginName("plugin").version("1").detectionSource("stack").build());
        return resolver;
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

    private static java.util.concurrent.ScheduledExecutorService maintenanceExecutor(
        UsageStatisticsService service) {
        try {
            var field = UsageStatisticsService.class.getDeclaredField("maintenance");
            field.setAccessible(true);
            return (java.util.concurrent.ScheduledExecutorService) field.get(service);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
