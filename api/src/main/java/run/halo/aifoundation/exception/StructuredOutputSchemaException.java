package run.halo.aifoundation.exception;

import run.halo.aifoundation.schema.OutputType;

/**
 * Raised when a requested structured-output schema cannot be represented safely by the selected
 * provider-native strict format.
 *
 * <p>This is a local request error. It is raised before provider invocation and identifies the
 * incompatible schema location through {@link #getValidationPath()}.
 */
public class StructuredOutputSchemaException extends AiFoundationException {

    private final OutputType outputType;
    private final String validationPath;

    public StructuredOutputSchemaException(String message, OutputType outputType,
        String validationPath) {
        super(message);
        this.outputType = outputType;
        this.validationPath = validationPath;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public String getValidationPath() {
        return validationPath;
    }
}
