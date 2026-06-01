package run.halo.aifoundation.exception;

import java.time.Duration;

/**
 * Raised when an embedding request exceeds its configured timeout.
 */
public class EmbeddingTimeoutException extends AiFoundationException {

    /**
     * Configured timeout duration that was exceeded.
     */
    private final Duration timeout;

    public EmbeddingTimeoutException(Duration timeout, Throwable cause) {
        super("AI embedding timed out after " + timeout, cause);
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
