package run.halo.aifoundation.exception;

import java.time.Duration;

/** Raised when decision inference exceeds its total timeout. */
public class DecisionTimeoutException extends AiFoundationException {
    private final Duration timeout;

    public DecisionTimeoutException(Duration timeout, Throwable cause) {
        super("AI decision inference timed out after " + timeout, cause);
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
