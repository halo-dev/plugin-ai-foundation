package run.halo.aifoundation.exception;

import java.time.Duration;

/**
 * Raised when text generation exceeds a configured total, step, or tool timeout.
 */
public class AiGenerationTimeoutException extends AiFoundationException {

    /**
     * Timeout scope, such as total request, step, or tool execution.
     */
    private final String scope;
    /**
     * Configured timeout duration that was exceeded.
     */
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
