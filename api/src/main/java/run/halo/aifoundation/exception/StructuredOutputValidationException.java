package run.halo.aifoundation.exception;

import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.schema.OutputType;

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

    /**
     * Structured output mode that failed validation.
     */
    private final OutputType outputType;

    /**
     * Raw text used for parsing and validation.
     */
    private final String outputText;

    /**
     * Best-effort path to the invalid value.
     */
    private final String validationPath;

    /**
     * Generation step index where validation failed.
     */
    private final Integer stepIndex;

    /**
     * Usage reported by the failing generation step, when available.
     */
    private final LanguageModelUsage usage;

    /**
     * Provider response metadata for the failing generation step, when available.
     */
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
