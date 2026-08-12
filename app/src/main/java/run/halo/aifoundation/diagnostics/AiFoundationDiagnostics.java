package run.halo.aifoundation.diagnostics;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.halo.aifoundation.chat.FinishReason;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.exception.StructuredOutputTerminationException;
import run.halo.aifoundation.exception.StructuredOutputValidationException;

/**
 * Production-safe failure summaries and opt-in full-content provider diagnostics.
 */
public final class AiFoundationDiagnostics {

    public static final String CORRELATION_ID_KEY = "aiFoundationDiagnosticId";
    public static final String LOGGER_NAME = "run.halo.aifoundation.diagnostics";

    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);
    private static final Pattern JSON_SECRET = Pattern.compile(
        "(?i)(\\\"(?:authorization|api[_-]?key|access[_-]?token|bearer[_-]?token)"
            + "\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
    private static final Pattern HEADER_SECRET = Pattern.compile(
        "(?im)(authorization|x-api-key|api-key)\\s*[:=]\\s*([^\\r\\n,}]+)");

    private AiFoundationDiagnostics() {
    }

    public static boolean isEnabled() {
        return LOGGER.isTraceEnabled();
    }

    public static String newInvocationId() {
        return "ai_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static void trace(String event, String diagnosticId, Supplier<String> details) {
        if (!isEnabled()) {
            return;
        }
        var suffix = details != null ? details.get() : null;
        LOGGER.trace("event={} diagnosticId={}{}", event,
            diagnosticId != null ? diagnosticId : "unavailable",
            suffix == null || suffix.isBlank() ? "" : " " + suffix);
    }

    /**
     * Records one content-free summary after structured output finally fails.
     *
     * <p>This intentionally accepts the typed terminal exception instead of raw request or response
     * values. Prompts, schemas, generated text, response bodies, headers, credentials, and raw usage
     * are never included.
     */
    public static void warnStructuredOutputFailure(StructuredOutputValidationException error,
        FinishReason finishReason, String rawFinishReason) {
        if (error == null || !LOGGER.isWarnEnabled()) {
            return;
        }
        var response = error.getResponse();
        var usage = error.getUsage();
        var termination = error instanceof StructuredOutputTerminationException value
            ? value : null;
        var normalizedFinishReason = termination != null
            ? termination.getFinishReason() : finishReason;
        var providerFinishReason = termination != null
            ? termination.getRawFinishReason() : rawFinishReason;
        LOGGER.warn("event=structured-output-failure-summary diagnosticId={} {}",
            diagnosticId(response), fields(
                "errorType", error.getClass().getSimpleName(),
                "rootCauseType", rootCauseType(error),
                "outputType", error.getOutputType(),
                "finishReason", normalizedFinishReason,
                "rawFinishReason", providerFinishReason,
                "validationPath", error.getValidationPath(),
                "stepIndex", error.getStepIndex(),
                "model", response != null ? response.getModel() : null,
                "responseId", response != null ? response.getId() : null,
                "inputTokens", usage != null ? usage.getInputTokens() : null,
                "outputTokens", usage != null ? usage.getOutputTokens() : null,
                "reasoningTokens", usage != null ? usage.getReasoningTokens() : null,
                "totalTokens", usage != null ? usage.getTotalTokens() : null,
                "outputChars", error.getOutputText() != null ? error.getOutputText().length() : null));
    }

    public static String fields(Object... namesAndValues) {
        if (namesAndValues == null || namesAndValues.length == 0) {
            return "";
        }
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Diagnostic fields require name-value pairs");
        }
        var fields = new StringBuilder();
        for (var i = 0; i < namesAndValues.length; i += 2) {
            if (!fields.isEmpty()) {
                fields.append(' ');
            }
            fields.append(namesAndValues[i]).append('=')
                .append(quoted(namesAndValues[i + 1]));
        }
        return fields.toString();
    }

    public static String redactSensitiveText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        var jsonRedacted = JSON_SECRET.matcher(value).replaceAll("$1[REDACTED]$2");
        return HEADER_SECRET.matcher(jsonRedacted).replaceAll("$1=[REDACTED]");
    }

    private static String diagnosticId(GenerationResponseMetadata response) {
        if (response == null || response.getMetadata() == null) {
            return "unavailable";
        }
        var value = response.getMetadata().get(CORRELATION_ID_KEY);
        return value != null ? value.toString() : "unavailable";
    }

    private static String rootCauseType(Throwable error) {
        var root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private static String quoted(Object value) {
        if (value == null) {
            return "null";
        }
        var text = redactSensitiveText(String.valueOf(value));
        return "\"" + text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            + "\"";
    }

}
