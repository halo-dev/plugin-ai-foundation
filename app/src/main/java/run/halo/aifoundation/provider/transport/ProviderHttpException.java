package run.halo.aifoundation.provider.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;

/**
 * A non-successful provider HTTP response with its status and structured error body preserved.
 */
public final class ProviderHttpException extends IllegalStateException {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_MESSAGE_BODY_LENGTH = 2_048;

    private final String providerType;
    private final String operation;
    private final int statusCode;
    private final String responseBody;
    private final Object errorBody;

    public ProviderHttpException(String providerType, String operation, int statusCode,
        String responseBody) {
        super(message(providerType, operation, statusCode, responseBody));
        this.providerType = providerType;
        this.operation = operation;
        this.statusCode = statusCode;
        this.responseBody = responseBody != null ? responseBody : "";
        this.errorBody = parseBody(this.responseBody);
    }

    public String getProviderType() {
        return providerType;
    }

    public String getOperation() {
        return operation;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Object getErrorBody() {
        return errorBody;
    }

    private static String message(String providerType, String operation, int statusCode,
        String body) {
        var redacted = AiFoundationDiagnostics.redactSensitiveText(body != null ? body : "");
        if (redacted.length() > MAX_MESSAGE_BODY_LENGTH) {
            redacted = redacted.substring(0, MAX_MESSAGE_BODY_LENGTH) + "…";
        }
        return providerType + " " + operation + " request failed: status=" + statusCode
            + ", body=" + redacted;
    }

    private static Object parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(body, Object.class);
        } catch (JsonProcessingException ignored) {
            return body;
        }
    }
}
