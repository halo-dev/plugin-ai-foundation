package run.halo.aifoundation;

import java.time.Duration;

public class EmbeddingTimeoutException extends AiFoundationException {

    private final Duration timeout;

    public EmbeddingTimeoutException(Duration timeout, Throwable cause) {
        super("AI embedding timed out after " + timeout, cause);
        this.timeout = timeout;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
