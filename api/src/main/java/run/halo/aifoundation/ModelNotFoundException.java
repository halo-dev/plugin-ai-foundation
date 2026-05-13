package run.halo.aifoundation;

public class ModelNotFoundException extends AiFoundationException {

    public ModelNotFoundException(String modelRef) {
        super("AI model not found: " + modelRef);
    }

    public ModelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
