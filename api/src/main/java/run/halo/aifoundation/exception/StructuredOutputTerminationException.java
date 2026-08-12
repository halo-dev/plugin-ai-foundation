package run.halo.aifoundation.exception;

import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.schema.OutputType;

/**
 * Raised when an explicit provider finish reason prevents a valid structured result from being
 * produced.
 *
 * <p>This exception remains a {@link StructuredOutputValidationException} so existing consumer
 * error handling continues to work. Callers that need recovery policies can inspect the normalized
 * and provider-native finish reasons to distinguish token exhaustion, content filtering, tool-call
 * termination, and provider errors from ordinary malformed JSON or schema violations.
 */
public class StructuredOutputTerminationException extends StructuredOutputValidationException {

    private final FinishReason finishReason;
    private final String rawFinishReason;

    public StructuredOutputTerminationException(String message, Throwable cause,
        OutputType outputType, String outputText, String validationPath, Integer stepIndex,
        LanguageModelUsage usage, GenerationResponseMetadata response, FinishReason finishReason,
        String rawFinishReason) {
        super(message, cause, outputType, outputText, validationPath, stepIndex, usage, response);
        this.finishReason = finishReason;
        this.rawFinishReason = rawFinishReason;
    }

    /**
     * Normalized provider-neutral finish reason.
     */
    public FinishReason getFinishReason() {
        return finishReason;
    }

    /**
     * Provider-native finish reason, when one was reported.
     */
    public String getRawFinishReason() {
        return rawFinishReason;
    }
}
