package run.halo.aifoundation;

public class AiGenerationCancelledException extends AiFoundationException {

    public AiGenerationCancelledException(String message) {
        super(message);
    }

    public AiGenerationCancelledException(String message, Throwable cause) {
        super(message, cause);
    }
}
