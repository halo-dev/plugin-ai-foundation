package run.halo.aifoundation;

/**
 * Raised when an {@code AiModel} exists but its configured model type does not match the requested
 * SDK interface.
 */
public class IncompatibleModelTypeException extends AiFoundationException {

    public IncompatibleModelTypeException(String modelName, String expectedType,
        String actualType) {
        super("AI model '" + modelName + "' has modelType '" + actualType
            + "', expected '" + expectedType + "'");
    }
}
