package run.halo.aifoundation.service.usage;

import static run.halo.aifoundation.service.usage.UsageStatisticsTestSupport.retain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallStart;
import run.halo.aifoundation.service.observation.UsageCallTerminal;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageExecutionRecord;
import run.halo.aifoundation.service.observation.UsageUnitKind;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.provider.support.ModelType;

class UsageRecoveryRegressionTest {
    @TempDir
    Path temp;

    private UsageCallStart start(String id, String time) {
        return new UsageCallStart(id, 1, Instant.parse(time), "plugin", "1", "test", null,
            "language.generateText", "LANGUAGE", "model", "provider", "openai", "gpt", false);
    }
    private UsageQuery range() {
        return new UsageQuery(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"),
            null, null, null, null, null, null, null, null, null);
    }

    @Test
    void linkageErrorRollsBackEntireSqliteBatch() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            var call = start("partial-batch", "2026-09-10T00:00:00Z");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.writeBatch(java.util.List.of(
                () -> store.startCall(call),
                () -> { throw new NoClassDefFoundError("injected-after-first-write"); }
            ))).isInstanceOf(NoClassDefFoundError.class);
            assertThat(store.getCall(call.id())).isEmpty();
        } finally {
            store.close();
        }
    }

    @Test
    void recoversDurableExecutionTokensAndArchivesThem() {
        var paths = new UsageDatabasePaths(temp);
        var store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        var call = start("interrupted", "2026-09-10T00:00:00Z");
        store.startCall(call);
        store.recordExecution(new UsageExecutionRecord("execution", call.id(), 1,
            UsageUnitKind.GENERATION_STEP, 0, 0, call.startedAt(), call.startedAt().plusSeconds(1),
            UsageStatus.SUCCEEDED, null, "gpt", "gpt",
            new NormalizedUsage(10L, 5L,null, null, null, null, null, null)));
        store.close(); // All existing writes are durable; only terminal event is absent.
        store = new SqliteUsageStatisticsStore(paths);
        store.initialize();
        store.reconcileAbandoned(Instant.parse("2026-09-11T00:00:00Z"));
        try {
            var detail = store.getCall(call.id()).orElseThrow();
            assertThat(detail.executions().getFirst().usage().accountedTotalTokens()).isEqualTo(15);
            assertThat(detail.call().usage().accountedTotalTokens()).isEqualTo(15);
            assertThat(detail.call().usage().quality()).isEqualTo(UsageQuality.PARTIAL);
            assertThat(detail.call().complete()).isFalse();
            assertThat(detail.call().attemptCount()).isEqualTo(1);
            assertThat(store.summary(range(), true).accountedTotalTokens()).isEqualTo(15);
            retain(store, Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
            assertThat(store.getCall(call.id()).orElseThrow().executions()).isEmpty();
            assertThat(store.summary(range(), true).accountedTotalTokens()).isEqualTo(15);
            assertThat(store.summary(range(), true).complete()).isFalse();
        } finally {
            store.close();
        }
    }

    @Test
    void startupRecoveryPersistsIncompleteHealthAndAffectedIntervalOnlyOnce() {
        var paths = new UsageDatabasePaths(temp);
        var call = start("interrupted", "2026-09-10T00:00:00Z");
        var previousSince = Instant.parse("2026-09-01T00:00:00Z");
        var previousUntil = Instant.parse("2026-09-02T00:00:00Z");
        var recoveredAt = Instant.parse("2026-09-11T00:00:00Z");
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            store.startCall(call);
            store.writeHealth(new UsageHealthState(2, 3, 4, previousUntil,
                previousSince, previousUntil, null, null));
        }
        var store = new SqliteUsageStatisticsStore(paths);
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class),
            Clock.fixed(recoveredAt, ZoneOffset.UTC));
        service.initialize();
        try {
            assertThat(store.getCall(call.id()).orElseThrow().call().status())
                .isEqualTo(UsageStatus.ABANDONED);
            assertThat(service.health().complete()).isFalse();
            assertThat(service.health().incompleteCalls()).isEqualTo(4);
            assertThat(service.health().affectedSince()).isEqualTo(previousSince);
            assertThat(service.health().affectedUntil()).isEqualTo(recoveredAt);
            assertThat(store.readHealth().incompleteCalls()).isEqualTo(4);
            assertThat(store.readHealth().droppedEvents()).isEqualTo(2);
            assertThat(store.readHealth().writeFailures()).isEqualTo(4);
            assertThat(store.readHealth().lastWriteErrorAt()).isEqualTo(previousUntil);
        } finally {
            service.close();
        }
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            var health = reopened.readHealth();
            reopened.reconcileAbandoned(recoveredAt.plusSeconds(60));
            assertThat(reopened.readHealth()).isEqualTo(health);
            reopened.reset();
            reopened.reconcileAbandoned(recoveredAt.plusSeconds(120));
            assertThat(reopened.readHealth().incompleteCalls()).isZero();
            assertThat(reopened.readHealth().affectedSince()).isNull();
            assertThat(reopened.readHealth().affectedUntil()).isNull();
        }
    }

    @Test
    void recoveryFromCleanHealthIncludesEveryAbandonedCall() {
        var paths = new UsageDatabasePaths(temp);
        var earliest = start("earliest", "2026-09-01T00:00:00Z");
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            store.startCall(earliest);
            store.startCall(start("latest", "2026-09-10T00:00:00Z"));
        }
        var recoveredAt = Instant.parse("2026-09-11T00:00:00Z");
        var store = new SqliteUsageStatisticsStore(paths);
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class),
            Clock.fixed(recoveredAt, ZoneOffset.UTC));
        service.initialize();
        try {
            assertThat(service.health().complete()).isFalse();
            assertThat(service.health().incompleteCalls()).isEqualTo(2);
            assertThat(service.health().affectedSince()).isEqualTo(earliest.startedAt());
            assertThat(service.health().affectedUntil()).isEqualTo(recoveredAt);
            assertThat(service.summary(range()).block().complete()).isFalse();
        } finally {
            service.close();
        }
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            assertThat(reopened.readHealth().incompleteCalls()).isEqualTo(2);
            assertThat(reopened.readHealth().affectedSince()).isEqualTo(earliest.startedAt());
            assertThat(reopened.readHealth().affectedUntil()).isEqualTo(recoveredAt);
        }
    }

    @Test
    void failedRecoveryRollsBackCallsAndHealthTogether() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            var call = start("interrupted", "2026-09-10T00:00:00Z");
            store.startCall(call);
            var health = store.readHealth();
            try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + paths.database(),
                new java.util.Properties()); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TRIGGER fail_recovery BEFORE UPDATE ON ai_calls
                    WHEN NEW.status = 'ABANDONED'
                    BEGIN SELECT RAISE(ABORT, 'injected recovery failure'); END
                    """);
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    store.reconcileAbandoned(Instant.parse("2026-09-11T00:00:00Z")))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(store.getCall(call.id()).orElseThrow().call().status())
                    .isEqualTo(UsageStatus.IN_PROGRESS);
                assertThat(store.readHealth()).isEqualTo(health);
                statement.executeUpdate("DROP TRIGGER fail_recovery");
            }
            store.reconcileAbandoned(Instant.parse("2026-09-11T00:00:00Z"));
            assertThat(store.readHealth().incompleteCalls()).isEqualTo(1);
        }
    }

    @Test
    void retainsCompletedFactsWithoutLosingActiveOrLateCalls() {
        var store = new SqliteUsageStatisticsStore(new UsageDatabasePaths(temp));
        store.initialize();
        try {
            store.startCall(start("terminal-event-lost", "2025-01-01T00:00:00Z"));
            var completed = start("completed", "2025-01-01T01:00:00Z");
            store.finishCall(new UsageCallTerminal(completed, completed.startedAt().plusSeconds(1),
                UsageStatus.SUCCEEDED, null, "gpt", 1, 0, 0, true,
                new NormalizedUsage(10L, 5L, null, null, null, null, null, null)));
            retain(store, Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC));
            assertThat(store.getCall(completed.id())).isPresent();
            assertThat(store.getCall("terminal-event-lost")).isPresent();
            assertThat(store.summary(range(), true).callCount()).isEqualTo(2);
            assertThat(store.trends(range(), true, null).stream().mapToLong(UsageTrendPoint::callCount).sum()).isEqualTo(2);
            var late = start("terminal-event-lost", "2025-01-01T00:00:00Z");
            store.finishCall(new UsageCallTerminal(late, Instant.parse("2026-09-11T00:00:00Z"),
                UsageStatus.SUCCEEDED, null, "gpt", 1, 0, 0, true,
                new NormalizedUsage(10L, 5L,null, null, null, null, null, null)));
            var maintenanceClock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC);
            retain(store, maintenanceClock);
            retain(store, maintenanceClock);
            assertThat(store.summary(range(), true).callCount()).isEqualTo(2);
            assertThat(store.summary(range(), true).accountedTotalTokens()).isEqualTo(30);
            assertThat(store.getCall(late.id())).isPresent();
        } finally {
            store.close();
        }
    }

    @Test
    void lostTerminalIsRecoveredWithoutRestartAndDoesNotAbandonLongRunningCalls() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        var store = new SqliteUsageStatisticsStore(paths) {
            @Override
            public synchronized void finishCall(UsageCallTerminal terminal) {
                throw new IllegalStateException("injected terminal failure");
            }
        };
        var clock = Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC);
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class), clock);
        service.initialize();
        var active = service.beginCall(descriptor());
        var ended = service.beginCall(descriptor());
        ended.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .succeed(new NormalizedUsage(10L, 5L, null, null, null, null, null, null), "gpt");
        ended.succeed("gpt", 1);
        service.close(); // Final drain also recovers permanently lost terminals.
        assertThat(service.health().incompleteCalls()).isEqualTo(1);
        assertThat(service.health().droppedEvents()).isEqualTo(1);
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            var recovered = reopened.getCall(ended.start().id()).orElseThrow();
            assertThat(recovered.call().status()).isEqualTo(UsageStatus.ABANDONED);
            assertThat(recovered.call().complete()).isFalse();
            assertThat(recovered.call().usage().accountedTotalTokens()).isEqualTo(15);
            assertThat(reopened.readHealth().incompleteCalls()).isEqualTo(1);
            assertThat(reopened.readHealth().affectedSince()).isEqualTo(clock.instant());
            assertThat(reopened.readHealth().affectedUntil()).isEqualTo(clock.instant());
            // Runtime recovery is idempotent and never expires an active call by age.
            reopened.reconcileAbandoned(clock.instant().plusSeconds(172800),
                java.util.Set.of(active.start().id()));
            retain(reopened,
                Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
            assertThat(reopened.getCall(ended.start().id()).orElseThrow().executions()).isEmpty();
            assertThat(reopened.getCall(active.start().id()).orElseThrow().call().status())
                .isEqualTo(UsageStatus.IN_PROGRESS);
            assertThat(reopened.readHealth().incompleteCalls()).isEqualTo(1);
        }
    }

    @Test
    void rejectedTerminalWaitsForItsQueuedStartBeforeRecovery() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        var store = new SqliteUsageStatisticsStore(paths);
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        var queueField = UsageStatisticsService.class.getDeclaredField("pendingWrites");
        queueField.setAccessible(true);
        queueField.set(service, new java.util.concurrent.ArrayBlockingQueue<>(1));
        service.initialize();
        var writerField = UsageStatisticsService.class.getDeclaredField("writer");
        writerField.setAccessible(true);
        var writer = (java.util.concurrent.ThreadPoolExecutor) writerField.get(service);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        writer.execute(() -> {
            entered.countDown();
            try {
                assertThat(release.await(4, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
        });
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var ended = service.beginCall(descriptor());
            ended.succeed("gpt", 0);
            assertThat(service.health().droppedEvents()).isEqualTo(1);
            release.countDown();
            service.close();
            try (var reopened = new SqliteUsageStatisticsStore(paths)) {
                reopened.initialize();
                assertThat(reopened.getCall(ended.start().id()).orElseThrow().call().status())
                    .isEqualTo(UsageStatus.ABANDONED);
                var health = reopened.readHealth();
                reopened.reconcileAbandoned(Instant.now());
                assertThat(reopened.readHealth()).isEqualTo(health);
                assertThat(health.incompleteCalls()).isEqualTo(1);
            }
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test
    void activeCallCapacityRejectsAdmissionAndResetRetiresOldSessions() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        when(store.reset()).thenReturn(2L);
        doAnswer(invocation -> {
            invocation.getArgument(0, java.util.List.class).forEach(action ->
                ((Runnable) action).run());
            return null;
        }).when(store).writeBatch(anyList());
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        try {
            var first = service.beginCall(descriptor());
            for (int i = 1; i < UsageStatisticsService.WRITE_QUEUE_CAPACITY; i++) {
                assertThat(service.beginCall(descriptor())).isNotNull();
            }
            assertThat(service.beginCall(descriptor())).isNull();
            assertThat(service.health().incompleteCalls()).isEqualTo(1);
            assertThat(service.health().droppedEvents()).isEqualTo(1);
            service.reset("RESET").block();
            first.succeed("gpt", 0);
            assertThat(service.health().complete()).isTrue();
            var current = service.beginCall(descriptor());
            assertThat(current).isNotNull();
            assertThat(current.start().epoch()).isEqualTo(2);
            current.succeed("gpt", 0);
        } finally {
            service.close();
        }
        verify(store, org.mockito.Mockito.never()).reconcileAbandoned(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void runtimeRecoveryFailureRollsBackAndCanRetryWithoutRecountingHealth() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            var ended = start("ended", "2026-09-10T00:00:00Z");
            store.startCall(ended);
            var at = ended.startedAt().plusSeconds(60);
            var health = new UsageHealthState(1, 1, 0, null, ended.startedAt(), at, null, null);
            store.writeHealth(health);
            try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + paths.database(),
                new java.util.Properties()); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TRIGGER fail_runtime_recovery BEFORE UPDATE ON ai_calls
                    WHEN NEW.status = 'ABANDONED'
                    BEGIN SELECT RAISE(ABORT, 'injected recovery failure'); END
                    """);
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    store.reconcileAbandoned(at, java.util.Set.of()))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(store.getCall(ended.id()).orElseThrow().call().status())
                    .isEqualTo(UsageStatus.IN_PROGRESS);
                assertThat(store.readHealth()).isEqualTo(health);
                statement.executeUpdate("DROP TRIGGER fail_runtime_recovery");
            }
            var active = new java.util.HashSet<String>();
            for (int i = 0; i < UsageStatisticsService.WRITE_QUEUE_CAPACITY; i++) {
                active.add("active-" + i);
            }
            store.reconcileAbandoned(at, active); // Exercise the full bound of SQL parameters.
            assertThat(store.getCall(ended.id()).orElseThrow().call().status())
                .isEqualTo(UsageStatus.ABANDONED);
            store.reconcileAbandoned(at);
            assertThat(store.readHealth()).isEqualTo(health);
        }
    }

    @Test
    void activeCallWithLostExecutionIsNotCountedAgainAfterRestart() {
        var paths = new UsageDatabasePaths(temp);
        var store = new SqliteUsageStatisticsStore(paths) {
            @Override
            public synchronized void recordExecution(UsageExecutionRecord execution) {
                throw new IllegalStateException("injected execution failure");
            }
        };
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        var session = service.beginCall(descriptor());
        session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
            .succeed(NormalizedUsage.missing(), "gpt");
        service.close();
        assertThat(service.health().incompleteCalls()).isEqualTo(1);
        var reopened = new SqliteUsageStatisticsStore(paths);
        var restarted = new UsageStatisticsService(reopened, mock(CallerPluginResolver.class));
        restarted.initialize();
        try {
            assertThat(reopened.getCall(session.start().id()).orElseThrow().call().status())
                .isEqualTo(UsageStatus.ABANDONED);
            assertThat(restarted.health().incompleteCalls()).isEqualTo(1);
        } finally {
            restarted.close();
        }
    }

    @Test
    void checkpointCreatesMissingStartAtomicallyAndLateStartPreservesCountedMarker() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        var call = start("pending-start", "2026-09-10T00:00:00Z");
        var at = call.startedAt().plusSeconds(60);
        var health = new UsageHealthState(1, 1, 0, null, call.startedAt(), at, null, null);
        try (var store = new SqliteUsageStatisticsStore(paths)) {
            store.initialize();
            try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + paths.database(),
                new java.util.Properties()); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TRIGGER fail_health_checkpoint BEFORE UPDATE ON ai_statistics_health
                    BEGIN SELECT RAISE(ABORT, 'injected checkpoint failure'); END
                    """);
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    store.writeHealth(health, java.util.List.of(call)))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(store.getCall(call.id())).isEmpty();
                assertThat(store.readHealth().incompleteCalls()).isZero();
                statement.executeUpdate("DROP TRIGGER fail_health_checkpoint");
            }
            store.writeHealth(health, java.util.List.of(call));
            store.startCall(call); // The original accepted start reaches SQLite later.
            assertThat(store.getCall(call.id()).orElseThrow().call().complete()).isFalse();
            assertThat(store.readHealth()).isEqualTo(health);
        }
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            reopened.reconcileAbandoned(at.plusSeconds(60));
            assertThat(reopened.readHealth().incompleteCalls()).isEqualTo(1);
            reopened.reset();
            reopened.writeHealth(reopened.readHealth(), java.util.List.of(call));
            assertThat(reopened.getCall(call.id())).isEmpty();
            assertThat(reopened.readHealth().incompleteCalls()).isZero();
        }
    }

    @Test
    void failureDuringHealthCheckpointRemainsDirtyForTheNextSnapshot() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        var store = new SqliteUsageStatisticsStore(paths) {
            @Override
            public synchronized void writeHealth(UsageHealthState health,
                java.util.List<UsageCallStart> incomplete) {
                if (once.compareAndSet(false, true)) {
                    entered.countDown();
                    try {
                        assertThat(release.await(4, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                }
                super.writeHealth(health, incomplete);
            }
        };
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        var mark = UsageStatisticsService.class.getDeclaredMethod("markIncomplete",
            run.halo.aifoundation.service.observation.UsageCallSession.class);
        mark.setAccessible(true);
        var checkpoint = UsageStatisticsService.class.getDeclaredMethod("persistHealthIfDirty");
        checkpoint.setAccessible(true);
        var writerField = UsageStatisticsService.class.getDeclaredField("writer");
        writerField.setAccessible(true);
        var writer = (java.util.concurrent.ThreadPoolExecutor) writerField.get(service);
        var first = service.beginCall(descriptor());
        var second = service.beginCall(descriptor());
        writer.submit(() -> { }).get(2, TimeUnit.SECONDS);
        try {
            mark.invoke(service, first);
            var persisted = writer.submit(() -> {
                try {
                    checkpoint.invoke(service);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            mark.invoke(service, second);
            release.countDown();
            persisted.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            service.close();
        }
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            reopened.reconcileAbandoned(Instant.now());
            assertThat(reopened.readHealth().incompleteCalls()).isEqualTo(2);
        }
    }

    @Test
    void failedCheckpointAndRetentionDoNotReinsertOrRecountCallFacts() throws Exception {
        var paths = new UsageDatabasePaths(temp);
        var failHealth = new java.util.concurrent.atomic.AtomicBoolean(true);
        var store = new SqliteUsageStatisticsStore(paths) {
            @Override
            public synchronized void recordExecution(UsageExecutionRecord execution) {
                throw new IllegalStateException("injected execution failure");
            }

            @Override
            public synchronized void writeHealth(UsageHealthState health,
                java.util.List<UsageCallStart> incomplete) {
                if (failHealth.get()) {
                    throw new IllegalStateException("injected checkpoint failure");
                }
                super.writeHealth(health, incomplete);
            }
        };
        var now = new java.util.concurrent.atomic.AtomicReference<>(
            Instant.parse("2026-01-01T00:00:00Z"));
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class), clock);
        service.initialize();
        var writerField = UsageStatisticsService.class.getDeclaredField("writer");
        writerField.setAccessible(true);
        var writer = (java.util.concurrent.ThreadPoolExecutor) writerField.get(service);
        var maintenance = UsageStatisticsService.class.getDeclaredMethod("maintainOneBatch");
        maintenance.setAccessible(true);
        Runnable maintain = () -> {
            try {
                maintenance.invoke(service);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        };
        try {
            var session = service.beginCall(descriptor());
            session.beginExecution(UsageUnitKind.GENERATION_STEP, 0)
                .succeed(new NormalizedUsage(10L, 5L, null, null, null, null, null, null), "gpt");
            session.succeed("gpt", 1);
            writer.submit(() -> { }).get(3, TimeUnit.SECONDS);
            now.set(Instant.parse("2026-10-01T00:00:00Z"));
            writer.submit(maintain).get(3, TimeUnit.SECONDS);
            assertThat(store.getCall(session.start().id())).isPresent();
            assertThat(store.summary(range(), false).callCount()).isEqualTo(1);
            failHealth.set(false);
            writer.submit(maintain).get(3, TimeUnit.SECONDS);
            assertThat(store.getCall(session.start().id())).isPresent();
            writer.submit(maintain).get(3, TimeUnit.SECONDS);
            assertThat(store.summary(range(), false).callCount()).isEqualTo(1);
            assertThat(store.summary(range(), false).accountedTotalTokens()).isEqualTo(15);
            assertThat(store.readHealth().incompleteCalls()).isEqualTo(1);
        } finally {
            failHealth.set(false);
            service.close();
        }
        try (var reopened = new SqliteUsageStatisticsStore(paths)) {
            reopened.initialize();
            reopened.reconcileAbandoned(now.get());
            assertThat(reopened.summary(range(), false).callCount()).isEqualTo(1);
            assertThat(reopened.summary(range(), false).accountedTotalTokens()).isEqualTo(15);
        }
    }

    private UsageCallDescriptor descriptor() {
        return new UsageCallDescriptor(new ModelCallContext(ModelType.LANGUAGE,
            "model", "provider", "openai", "gpt"), "language.generateText", false, null,
            CallerPluginInfo.builder().pluginName("plugin").detectionSource("test").build());
    }

    @Test
    void writerLinkageFailureDisablesStatisticsAndReportsLostEvents() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var attempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            attempted.countDown();
            throw new NoClassDefFoundError("injected-driver-linkage");
        })
            .when(store).writeBatch(anyList());
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        try {
            var caller = CallerPluginInfo.builder().pluginName("plugin").version("1")
                .detectionSource("test").build();
            var session = service.beginCall(new UsageCallDescriptor(new ModelCallContext(
                ModelType.LANGUAGE, "model", "provider", "openai", "gpt"), "language.generateText",false, null, caller));
            assertThat(attempted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(service.health().queueDepth()).isZero();
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (service.health().droppedEvents() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(service.health().complete()).isFalse();
            assertThat(service.health().available()).isFalse();
            assertThat(service.health().droppedEvents()).isEqualTo(1);
            assertThat(service.health().writeFailures()).isEqualTo(1);
            assertThat(session.isIncomplete()).isTrue();
            verify(store, times(1)).writeBatch(anyList());
        } finally {
            service.close();
        }
    }
}
