package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;

class UsageShutdownRegressionTest {

    @Test
    void orderlyShutdownDrainsAllAcceptedEvents() throws Exception {
        var store = mock(UsageStatisticsStore.class, CALLS_REAL_METHODS);
        when(store.currentEpoch()).thenReturn(1L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        doAnswer(invocation -> {
            if (count.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(store).startCall(any());
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        try {
            var descriptor = new UsageCallDescriptor(new ModelCallContext(ModelType.LANGUAGE,
                "m", "p", "openai", "gpt"), "language.generateText", false, null,
                CallerPluginInfo.builder().pluginName("plugin").build());
            service.beginCall(descriptor);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 1500; i++) {
                service.beginCall(descriptor);
            }
            var closed = CompletableFuture.runAsync(service::close);
            var field = UsageStatisticsService.class.getDeclaredField("writer");
            field.setAccessible(true);
            var writer = (ThreadPoolExecutor) field.get(service);
            for (int i = 0; i < 200 && !writer.isShutdown(); i++) {
                Thread.sleep(10);
            }
            assertThat(writer.isShutdown()).isTrue();
            release.countDown();
            closed.get(4, TimeUnit.SECONDS);
            assertThat(count.get()).isEqualTo(1501);
            assertThat(service.health().droppedEvents()).isZero();
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test
    void shutdownClosesStoreEvenWhenReaderExceedsTimeout() throws Exception {
        var store = mock(UsageStatisticsStore.class);
        when(store.currentEpoch()).thenReturn(1L);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            var done = false;
            while (!done) {
                try {
                    done = release.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    // Simulate a driver that does not respond to thread interruption.
                }
            }
            finished.countDown();
            return Optional.empty();
        }).when(store).getCall(anyString());
        var service = new UsageStatisticsService(store, mock(CallerPluginResolver.class));
        service.initialize();
        service.getCall("call").subscribe();
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            service.close();
        } finally {
            release.countDown();
        }
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        verify(store).close();
        service.close();
        verify(store).close();
    }
}
