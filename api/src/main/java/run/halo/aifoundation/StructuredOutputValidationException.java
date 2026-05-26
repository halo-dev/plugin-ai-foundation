package run.halo.aifoundation;

/**
 * Raised when a model or tool result does not satisfy the requested structured schema.
 */
public class StructuredOutputValidationException extends RuntimeException {

    public StructuredOutputValidationException(String message) {
        super(message);
    }

    public StructuredOutputValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
