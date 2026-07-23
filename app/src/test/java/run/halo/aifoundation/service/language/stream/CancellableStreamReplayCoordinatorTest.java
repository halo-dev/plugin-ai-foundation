package run.halo.aifoundation.service.language.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

class CancellableStreamReplayCoordinatorTest {

    @Test
    void staysLazyAndReplaysSuccessfulRunWithoutReconnect() {
        var subscriptions = new AtomicInteger();
        var coordinator = new CancellableStreamReplayCoordinator<>(
            Flux.just(1, 2, 3).doOnSubscribe(ignored -> subscriptions.incrementAndGet()),
            () -> -1);
        var replay = coordinator.flux();

        assertThat(subscriptions).hasValue(0);
        StepVerifier.create(replay).expectNext(1, 2, 3).verifyComplete();
        StepVerifier.create(replay).expectNext(1, 2, 3).verifyComplete();
        assertThat(subscriptions).hasValue(1);
    }

    @Test
    void propagatesDemandToSourceOneEventAtATime() {
        var requested = new AtomicLong();
        var coordinator = new CancellableStreamReplayCoordinator<>(
            Flux.range(1, 3).doOnRequest(requested::addAndGet), () -> -1);

        StepVerifier.create(coordinator.flux(), 0)
            .then(() -> assertThat(requested).hasValue(0))
            .thenRequest(1)
            .expectNext(1)
            .then(() -> assertThat(requested).hasValue(1))
            .thenRequest(2)
            .expectNext(2, 3)
            .verifyComplete();

        assertThat(requested).hasValue(3);
    }

    @Test
    void keepsRunForAnotherViewAndCancelsAfterLastViewLeaves() {
        var sourceSink = new AtomicReference<FluxSink<Integer>>();
        var sourceCancelled = new AtomicBoolean();
        var source = Flux.<Integer>create(sink -> {
            sourceSink.set(sink);
            sink.onCancel(() -> sourceCancelled.set(true));
        });
        var coordinator = new CancellableStreamReplayCoordinator<>(source, () -> -1);
        var replay = coordinator.flux();
        var firstValues = new ArrayList<Integer>();
        var secondValues = new ArrayList<Integer>();

        Disposable first = replay.subscribe(firstValues::add);
        Disposable second = replay.subscribe(secondValues::add);
        sourceSink.get().next(1);
        first.dispose();
        assertThat(sourceCancelled).isFalse();

        sourceSink.get().next(2);
        second.dispose();

        assertThat(sourceCancelled).isTrue();
        assertThat(firstValues).containsExactly(1);
        assertThat(secondValues).containsExactly(1, 2);
        StepVerifier.create(replay)
            .expectNext(1, 2, -1)
            .verifyComplete();
    }

    @Test
    void replaysFailureWithoutReconnect() {
        var subscriptions = new AtomicInteger();
        var failure = new IllegalStateException("provider failed");
        var coordinator = new CancellableStreamReplayCoordinator<>(
            Flux.concat(Flux.just(1), Flux.error(failure))
                .doOnSubscribe(ignored -> subscriptions.incrementAndGet()),
            () -> -1);
        var replay = coordinator.flux();

        StepVerifier.create(replay).expectNext(1).expectErrorMessage("provider failed").verify();
        StepVerifier.create(replay).expectNext(1).expectErrorMessage("provider failed").verify();
        assertThat(subscriptions).hasValue(1);
    }
}
