package run.halo.aifoundation.exception;

/**
 * Raised when an embedding request is cancelled through a cancellation token.
 */
public class EmbeddingCancelledException extends AiFoundationException {

    public EmbeddingCancelledException(String message) {
        super(message);
    }

    public EmbeddingCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
