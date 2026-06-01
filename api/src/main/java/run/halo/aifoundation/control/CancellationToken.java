package run.halo.aifoundation.control;

import run.halo.aifoundation.exception.AiGenerationCancelledException;

/**
 * Provider-neutral cancellation signal for request-scoped SDK calls.
 *
 * <p>Streaming callers can still cancel a Reactor subscription. A token is useful when the caller
 * wants to stop non-streaming work or share one cancellation signal between stream projections and
 * surrounding application logic.
 */
public interface CancellationToken {

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new AiGenerationCancelledException("Generation was cancelled");
        }
    }
}
