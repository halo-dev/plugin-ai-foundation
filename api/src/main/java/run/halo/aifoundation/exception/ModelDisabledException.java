package run.halo.aifoundation.exception;

/**
 * Raised when a caller tries to use a disabled model.
 */
public class ModelDisabledException extends AiFoundationException {

    public ModelDisabledException(String modelName) {
        super("AI model is disabled: " + modelName);
    }
}
