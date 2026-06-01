package run.halo.aifoundation.control;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable source that owns a {@link CancellationToken}.
 *
 * <pre>{@code
 * var source = new CancellationSource();
 * var request = GenerateTextRequest.builder()
 *     .prompt("Long running work")
 *     .cancellationToken(source.token())
 *     .build();
 *
 * Disposable subscription = model.streamText(request).fullStream().subscribe();
 * source.cancel();
 * subscription.dispose();
 * }</pre>
 */
public class CancellationSource {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CancellationToken token = cancelled::get;

    public CancellationToken token() {
        return token;
    }

    public void cancel() {
        cancelled.set(true);
    }
}
