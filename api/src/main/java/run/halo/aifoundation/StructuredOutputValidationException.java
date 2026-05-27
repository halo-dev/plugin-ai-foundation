package run.halo.aifoundation;

/**
 * Raised when a model or tool result does not satisfy the requested structured schema.
 *
 * <p>The exception carries safe debugging context for callers that need to show or log validation
 * failures without depending on provider-specific error shapes:
 *
 * <pre>{@code
 * model.streamText(request)
 *     .output()
 *     .onErrorResume(StructuredOutputValidationException.class, error -> {
 *         log.warn("Structured output failed at {}", error.getValidationPath());
 *         return Mono.empty();
 *     });
 * }</pre>
 */
public class StructuredOutputValidationException extends RuntimeException {

    private final OutputType outputType;

    private final String outputText;

    private final String validationPath;

    private final Integer stepIndex;

    private final LanguageModelUsage usage;

    private final GenerationResponseMetadata response;

    public StructuredOutputValidationException(String message) {
        this(message, null, null, null, null, null, null, null);
    }

    public StructuredOutputValidationException(String message, Throwable cause) {
        this(message, cause, null, null, null, null, null, null);
    }

    public StructuredOutputValidationException(String message, Throwable cause,
        OutputType outputType, String outputText, String validationPath, Integer stepIndex,
        LanguageModelUsage usage, GenerationResponseMetadata response) {
        super(message, cause);
        this.outputType = outputType;
        this.outputText = outputText;
        this.validationPath = validationPath;
        this.stepIndex = stepIndex;
        this.usage = usage;
        this.response = response;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public String getOutputText() {
        return outputText;
    }

    public String getValidationPath() {
        return validationPath;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public LanguageModelUsage getUsage() {
        return usage;
    }

    public GenerationResponseMetadata getResponse() {
        return response;
    }
}
