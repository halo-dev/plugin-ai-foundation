package run.halo.aifoundation.service.language.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.context.Context;

/**
 * Lazily runs one source subscription, replays its complete history, and cancels the source when
 * the last live view is cancelled.
 *
 * <p>Unlike Reactor {@code cache()}, this coordinator requests source values one at a time only
 * while at least one caught-up subscriber has demand. A cancelled run is completed with a supplied
 * terminal value so later subscribers observe the partial history without reconnecting.
 */
public final class CancellableStreamReplayCoordinator<T> {
    private final Flux<T> source;
    private final Supplier<T> cancellationTerminal;
    private final List<T> history = new ArrayList<>();
    private final List<ReplaySubscription> subscribers = new ArrayList<>();

    private Subscription upstream;
    private boolean started;
    private boolean upstreamRequested;
    private boolean terminal;
    private Throwable terminalError;

    public CancellableStreamReplayCoordinator(Flux<T> source,
        Supplier<T> cancellationTerminal) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.cancellationTerminal = Objects.requireNonNull(cancellationTerminal,
            "cancellationTerminal must not be null");
    }

    /** Returns a lazy replay view over the single coordinated source run. */
    public Flux<T> flux() {
        return Flux.create(this::register, FluxSink.OverflowStrategy.ERROR);
    }

    private void register(FluxSink<T> sink) {
        var replay = new ReplaySubscription(sink);
        synchronized (this) {
            subscribers.add(replay);
            sink.onRequest(replay::request);
            sink.onCancel(replay::cancel);
            if (!started && !terminal) {
                started = true;
                source.subscribe(new SourceSubscriber(Context.of(sink.contextView())));
            }
            replay.drain();
            requestSourceIfNeeded();
        }
    }

    private void accept(T value) {
        synchronized (this) {
            if (terminal) {
                return;
            }
            upstreamRequested = false;
            history.add(value);
            drainSubscribers();
            requestSourceIfNeeded();
        }
    }

    private void complete() {
        terminate(null);
    }

    private void fail(Throwable error) {
        terminate(error);
    }

    private void terminate(Throwable error) {
        synchronized (this) {
            if (terminal) {
                return;
            }
            upstreamRequested = false;
            terminalError = error;
            terminal = true;
            drainSubscribers();
        }
    }

    private void drainSubscribers() {
        for (var subscriber : List.copyOf(subscribers)) {
            subscriber.drain();
        }
    }

    private void requestSourceIfNeeded() {
        if (terminal || upstream == null || upstreamRequested) {
            return;
        }
        var hasDemand = subscribers.stream().anyMatch(ReplaySubscription::awaitingSource);
        if (hasDemand) {
            upstreamRequested = true;
            upstream.request(1);
        }
    }

    private void cancelIfLastSubscriber() {
        if (terminal || !subscribers.isEmpty()) {
            return;
        }
        terminal = true;
        upstreamRequested = false;
        if (upstream != null) {
            upstream.cancel();
        }
        history.add(cancellationTerminal.get());
    }

    private static long addCap(long current, long requested) {
        var total = current + requested;
        return total < 0 ? Long.MAX_VALUE : total;
    }

    private final class ReplaySubscription {
        private final FluxSink<T> sink;
        private int cursor;
        private long requested;
        private boolean closed;

        private ReplaySubscription(FluxSink<T> sink) {
            this.sink = sink;
        }

        private void request(long count) {
            if (count <= 0) {
                return;
            }
            synchronized (CancellableStreamReplayCoordinator.this) {
                if (closed) {
                    return;
                }
                requested = addCap(requested, count);
                drain();
                requestSourceIfNeeded();
            }
        }

        private void drain() {
            while (!closed && requested > 0 && cursor < history.size()) {
                var value = history.get(cursor++);
                if (requested != Long.MAX_VALUE) {
                    requested--;
                }
                sink.next(value);
            }
            if (!closed && terminal && cursor == history.size()) {
                closed = true;
                subscribers.remove(this);
                if (terminalError != null) {
                    sink.error(terminalError);
                } else {
                    sink.complete();
                }
            }
        }

        private boolean awaitingSource() {
            return !closed && cursor == history.size() && requested > 0;
        }

        private void cancel() {
            synchronized (CancellableStreamReplayCoordinator.this) {
                if (closed) {
                    return;
                }
                closed = true;
                subscribers.remove(this);
                cancelIfLastSubscriber();
            }
        }
    }

    private final class SourceSubscriber implements CoreSubscriber<T> {
        private final Context context;

        private SourceSubscriber(Context context) {
            this.context = context;
        }

        @Override
        public Context currentContext() {
            return context;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            synchronized (CancellableStreamReplayCoordinator.this) {
                if (terminal) {
                    subscription.cancel();
                    return;
                }
                upstream = subscription;
                requestSourceIfNeeded();
            }
        }

        @Override
        public void onNext(T value) {
            accept(value);
        }

        @Override
        public void onError(Throwable error) {
            fail(error);
        }

        @Override
        public void onComplete() {
            complete();
        }
    }
}
