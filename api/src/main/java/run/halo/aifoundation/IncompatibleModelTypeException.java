package run.halo.aifoundation;

public class IncompatibleModelTypeException extends AiFoundationException {

    public IncompatibleModelTypeException(String modelName, String expectedType,
        String actualType) {
        super("AI model '" + modelName + "' has modelType '" + actualType
            + "', expected '" + expectedType + "'");
    }
}
