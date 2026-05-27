package run.halo.aifoundation;

public class EmbeddingCancelledException extends AiFoundationException {

    public EmbeddingCancelledException(String message) {
        super(message);
    }

    public EmbeddingCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
