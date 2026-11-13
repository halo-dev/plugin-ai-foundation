package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;

/**
 * Exercises contention, cancellation and lease cleanup at the shared content-mutation boundary.
 *
 * <p>Tests use explicit gates so overlapping native requests and publication are ordered without sleeping.
 */
class HaloContentMutationCoordinatorTest {
    private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();

    @Test
    void defersSameResourceMutationsAndHandsOffInSubscriptionOrder() throws Exception {
        var gate = Sinks.<Void>empty();
        var order = Collections.synchronizedList(new ArrayList<Integer>());
        var completed = new CountDownLatch(3);
        var first = coordinator
                .withLock(Post.class, "article", () -> {
                    order.add(1);
                    return gate.asMono();
                })
                .doFinally(signal -> completed.countDown())
                .subscribe();
        coordinator
                .withLock(Post.class, "article", () -> Mono.fromRunnable(() -> order.add(2)))
                .doFinally(signal -> completed.countDown())
                .subscribe();
        coordinator
                .withLock(Post.class, "article", () -> Mono.fromRunnable(() -> order.add(3)))
                .doFinally(signal -> completed.countDown())
                .subscribe();
        assertThat(order).containsExactly(1);
        gate.tryEmitEmpty();
        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly(1, 2, 3);
        assertThat(coordinator.activeKeyCount()).isZero();
        first.dispose();
    }

    @Test
    void allowsDifferentNamesAndResourceKindsToProceedIndependently() {
        var hold =
                coordinator.withLock(Post.class, "article", Mono::<Void>never).subscribe();
        try {
            StepVerifier.create(coordinator.withLock(Post.class, "another", () -> Mono.just("another")))
                    .expectNext("another")
                    .verifyComplete();
            StepVerifier.create(coordinator.withLock(SinglePage.class, "article", () -> Mono.just("page")))
                    .expectNext("page")
                    .verifyComplete();
            assertThat(coordinator.activeKeyCount()).isEqualTo(1);
        } finally {
            hold.dispose();
        }
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void releasesAfterErrorAndAfterTheActiveMutationIsCancelled() throws Exception {
        StepVerifier.create(coordinator.withLock(
                        Post.class, "article", () -> Mono.error(new IllegalStateException("failed"))))
                .expectErrorMessage("failed")
                .verify();
        assertThat(coordinator.activeKeyCount()).isZero();
        var hold =
                coordinator.withLock(Post.class, "article", Mono::<Void>never).subscribe();
        var entered = new CountDownLatch(1);
        coordinator
                .withLock(Post.class, "article", () -> Mono.fromRunnable(entered::countDown))
                .subscribe();
        assertThat(entered.getCount()).isEqualTo(1);
        hold.dispose();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        StepVerifier.create(coordinator.withLock(Post.class, "article", Mono::<Void>empty))
                .verifyComplete();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void removesCancelledWaitersWithoutInvokingTheirMutation() {
        var hold =
                coordinator.withLock(Post.class, "article", Mono::<Void>never).subscribe();
        var invocations = new AtomicInteger();
        var waiting = coordinator
                .withLock(Post.class, "article", () -> Mono.fromRunnable(invocations::incrementAndGet))
                .subscribe();
        waiting.dispose();
        hold.dispose();
        StepVerifier.create(coordinator.withLock(Post.class, "article", Mono::<Void>empty))
                .verifyComplete();
        assertThat(invocations).hasValue(0);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void cancellationDuringQueuedLeaseHandoffDoesNotStrandTheFollowingMutation() {
        for (int attempt = 0; attempt < 200; attempt++) {
            var hold = coordinator
                    .withLock(Post.class, "article", Mono::<Void>never)
                    .subscribe();
            var waiting = coordinator
                    .withLock(Post.class, "article", Mono::<Void>never)
                    .subscribe();
            hold.dispose();
            waiting.dispose();
            StepVerifier.create(coordinator.withLock(Post.class, "article", () -> Mono.just("next")))
                    .expectNext("next")
                    .verifyComplete();
            assertThat(coordinator.activeKeyCount()).isZero();
        }
    }

    @Test
    void manySynchronousQueuedMutationsCompleteWithoutRecursiveLeaseHandoffs() throws Exception {
        var gate = Sinks.<Void>empty();
        coordinator.withLock(Post.class, "article", gate::asMono).subscribe();
        var completed = new CountDownLatch(1_000);
        for (int i = 0; i < 1_000; i++) {
            coordinator
                    .withLock(Post.class, "article", Mono::<Void>empty)
                    .doFinally(signal -> completed.countDown())
                    .subscribe();
        }
        gate.tryEmitEmpty();
        assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(coordinator.activeKeyCount()).isZero();
    }
}
