package run.halo.aifoundation.ui;

import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.control.CancellationToken;

/**
 * Caller-owned cancellation helper for UI message streams.
 *
 * <p>Example:
 * <pre>{@code
 * UIMessageCancellation cancellation = UIMessageCancellations.create();
 * UIMessageChatResult<MyMetadata> result = UIMessageChatHandlers.streamText(options -> options
 *     .model(model)
 *     .chatRequest(request)
 *     .cancellationToken(cancellation.token()));
 *
 * return cancellation.cancelWhenSubscriberCancels(result.response().body());
 * }</pre>
 */
public final class UIMessageCancellation {

    private final CancellationSource source = new CancellationSource();

    UIMessageCancellation() {
    }

    /**
     * Returns the token to pass into chat or stream options.
     *
     * @return cancellation token
     */
    public CancellationToken token() {
        return source.token();
    }

    /**
     * Requests cancellation for the associated stream or model call.
     */
    public void cancel() {
        source.cancel();
    }

    /**
     * Returns whether cancellation has been requested.
     *
     * @return {@code true} after {@link #cancel()} is called
     */
    public boolean isCancellationRequested() {
        return token().isCancellationRequested();
    }

    /**
     * Requests cancellation when the subscriber cancels a Flux subscription.
     *
     * @param publisher publisher to wrap
     * @param <T> element type
     * @return publisher with cancellation side effect
     */
    public <T> Flux<T> cancelWhenSubscriberCancels(Flux<T> publisher) {
        return Objects.requireNonNull(publisher, "publisher must not be null")
            .doFinally(signal -> cancelOnSubscriberCancel(signal));
    }

    /**
     * Requests cancellation when the subscriber cancels a Mono subscription.
     *
     * @param publisher publisher to wrap
     * @param <T> element type
     * @return publisher with cancellation side effect
     */
    public <T> Mono<T> cancelWhenSubscriberCancels(Mono<T> publisher) {
        return Objects.requireNonNull(publisher, "publisher must not be null")
            .doFinally(signal -> cancelOnSubscriberCancel(signal));
    }

    private void cancelOnSubscriberCancel(SignalType signal) {
        if (signal == SignalType.CANCEL) {
            cancel();
        }
    }
}
