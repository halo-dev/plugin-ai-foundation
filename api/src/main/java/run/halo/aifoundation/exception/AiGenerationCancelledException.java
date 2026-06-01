package run.halo.aifoundation.exception;

/**
 * Raised when a text generation request is cancelled through a cancellation token.
 */
public class AiGenerationCancelledException extends AiFoundationException {

    public AiGenerationCancelledException(String message) {
        super(message);
    }

    public AiGenerationCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
