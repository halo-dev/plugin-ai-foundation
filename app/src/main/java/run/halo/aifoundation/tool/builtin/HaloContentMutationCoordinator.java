package run.halo.aifoundation.tool.builtin;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.extension.Extension;

/**
 * Serializes content mutations that share an article or page, without blocking an HTTP event loop.
 *
 * <p>The native request filter acquires this lease before invoking Halo's handler, while shared publication tools use
 * the same coordinator before reading their inputs. For example, {@code withLock(Post.class, "hello", operation)} waits
 * for an existing Console save of that article, but does not delay a page or another article.
 *
 * <p>Leases are FIFO, scoped to subscriptions and released on completion, error or cancellation. This coordinates the
 * local plugin and native HTTP paths. It does not intercept direct extension-client writes performed by other plugins.
 */
@Component
public class HaloContentMutationCoordinator {
    private final Map<ContentKey, Queue> queues = new HashMap<>();

    /**
     * Runs a deferred mutation while holding the lease for one article or page.
     *
     * @param type {@link Post} or {@link SinglePage}
     * @param name persisted content resource name
     * @param operation work constructed only after acquisition
     * @param <T> operation result
     * @return the mutation result, releasing the lease on every terminal signal
     */
    public <T> Mono<T> withLock(Class<? extends Extension> type, String name, Supplier<Mono<T>> operation) {
        return Mono.defer(() -> {
            var key = key(type, name);
            return Mono.usingWhen(
                    acquire(key),
                    lease -> Mono.defer(operation),
                    lease -> Mono.fromRunnable(lease::close),
                    (lease, error) -> Mono.fromRunnable(lease::close),
                    lease -> Mono.fromRunnable(lease::close));
        });
    }

    private Mono<Lease> acquire(ContentKey key) {
        return Mono.<Lease>create(sink -> {
                    var waiter = new Waiter(key, sink);
                    sink.onCancel(() -> cancel(waiter));
                    Lease acquired;
                    synchronized (queues) {
                        if (waiter.cancelled) {
                            return;
                        }
                        var queue = queues.computeIfAbsent(key, ignored -> new Queue());
                        if (queue.owner != null) {
                            queue.waiters.addLast(waiter);
                            return;
                        }
                        acquired = grant(queue, waiter);
                    }
                    sink.success(acquired);
                })
                .doOnDiscard(Lease.class, Lease::close);
    }

    private Lease grant(Queue queue, Waiter waiter) {
        var lease = new Lease(waiter.key);
        waiter.lease = lease;
        queue.owner = lease;
        return lease;
    }

    private void cancel(Waiter waiter) {
        Lease acquired;
        synchronized (queues) {
            waiter.cancelled = true;
            acquired = waiter.lease;
            if (acquired == null) {
                var queue = queues.get(waiter.key);
                if (queue != null) {
                    queue.waiters.remove(waiter);
                }
            }
        }
        if (acquired != null) {
            acquired.close();
        }
    }

    private void release(Lease lease) {
        Waiter next = null;
        synchronized (queues) {
            var queue = queues.get(lease.key);
            if (queue == null || queue.owner != lease) {
                return;
            }
            queue.owner = null;
            while (!queue.waiters.isEmpty()) {
                var candidate = queue.waiters.removeFirst();
                if (candidate.cancelled) {
                    continue;
                }
                grant(queue, candidate);
                next = candidate;
                break;
            }
            if (next == null) {
                queues.remove(lease.key);
            }
        }
        if (next != null) {
            var granted = next;
            // A scheduled handoff avoids recursive completion when many queued mutations finish synchronously.
            Schedulers.parallel().schedule(() -> granted.sink.success(granted.lease));
        }
    }

    private static ContentKey key(Class<? extends Extension> type, String name) {
        if (type != Post.class && type != SinglePage.class) {
            throw new IllegalArgumentException("Only article and page mutations may be coordinated");
        }
        if (!BuiltInToolInputs.validResourceName(name)) {
            throw new IllegalArgumentException("Content name must be a Halo metadata.name");
        }
        return new ContentKey(type, name);
    }

    int activeKeyCount() {
        synchronized (queues) {
            return queues.size();
        }
    }

    /** Identifies one native content resource independently of its current snapshot references. */
    private record ContentKey(Class<? extends Extension> type, String name) {}

    /** Holds the current lease and FIFO waiters for one key; access is guarded by the queues monitor. */
    private final class Queue {
        private Lease owner;
        private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
    }

    /** Tracks cancellation both before queueing and during the handoff to a new lease holder. */
    private final class Waiter {
        private final ContentKey key;
        private final MonoSink<Lease> sink;
        private boolean cancelled;
        private Lease lease;

        private Waiter(ContentKey key, MonoSink<Lease> sink) {
            this.key = key;
            this.sink = sink;
        }
    }

    /**
     * Releases exactly one ownership turn, including cancellation races during acquisition.
     *
     * <p>The idempotent close also makes discarded acquisition results safe to clean up.
     */
    private final class Lease {
        private final ContentKey key;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(ContentKey key) {
            this.key = key;
        }

        private void close() {
            if (closed.compareAndSet(false, true)) {
                release(this);
            }
        }
    }
}
