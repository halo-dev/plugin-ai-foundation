package run.halo.aifoundation.exception;

/**
 * Raised when a reranking request is cancelled through a cancellation token.
 */
public class RerankCancelledException extends AiFoundationException {

    public RerankCancelledException(String message) {
        super(message);
    }

    public RerankCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
