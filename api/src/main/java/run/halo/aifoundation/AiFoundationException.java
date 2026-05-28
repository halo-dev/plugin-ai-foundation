package run.halo.aifoundation;

/**
 * Base runtime exception for provider-neutral AI Foundation SDK failures.
 */
public class AiFoundationException extends RuntimeException {

    public AiFoundationException(String message) {
        super(message);
    }

    public AiFoundationException(String message, Throwable cause) {
        super(message, cause);
    }
}
