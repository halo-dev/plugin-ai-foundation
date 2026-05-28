package run.halo.aifoundation;

import java.time.Duration;

/**
 * Raised when text generation exceeds a configured total, step, or tool timeout.
 */
public class AiGenerationTimeoutException extends AiFoundationException {

    private final String scope;
    private final Duration timeout;

    public AiGenerationTimeoutException(String scope, String message) {
        super(message);
        this.scope = scope;
        this.timeout = null;
    }

    public AiGenerationTimeoutException(String scope, Duration timeout, Throwable cause) {
        super("AI generation " + scope + " timed out after " + timeout, cause);
        this.scope = scope;
        this.timeout = timeout;
    }

    public String getScope() {
        return scope;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
