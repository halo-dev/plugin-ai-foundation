package run.halo.aifoundation;

/**
 * Raised when no {@code AiModel} resource exists for the requested {@code AiModel.metadata.name}.
 */
public class ModelNotFoundException extends AiFoundationException {

    public ModelNotFoundException(String modelName) {
        super("AI model not found: " + modelName);
    }

    public ModelNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
