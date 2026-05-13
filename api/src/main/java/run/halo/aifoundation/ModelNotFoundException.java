package run.halo.aifoundation;

public class ModelNotFoundException extends AiFoundationException {

    public ModelNotFoundException(String modelName) {
        super("AI model not found: " + modelName);
    }

    public ModelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
