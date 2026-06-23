package run.halo.aifoundation.exception;

import java.time.Duration;

/**
 * Raised when a reranking request exceeds its configured timeout.
 */
public class RerankTimeoutException extends AiFoundationException {

    private final Duration timeout;

    public RerankTimeoutException(Duration timeout, Throwable cause) {
        super("AI reranking timed out after " + timeout, cause);
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
