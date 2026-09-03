package run.halo.aifoundation.provider.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;

/**
 * Consistent, opt-in and credential-safe diagnostics for provider transports.
 */
public final class ProviderDiagnostics {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String providerType;
    private final String adapterType;
    private final String invocationId;

    private ProviderDiagnostics(String providerType, String adapterType) {
        this.providerType = providerType;
        this.adapterType = adapterType;
        this.invocationId = AiFoundationDiagnostics.newInvocationId();
    }

    public static ProviderDiagnostics create(String providerType, String adapterType) {
        return new ProviderDiagnostics(providerType, adapterType);
    }

    public String invocationId() {
        return invocationId;
    }

    public void request(String url, Object body, boolean stream) {
        AiFoundationDiagnostics.trace("provider-request", invocationId,
            () -> AiFoundationDiagnostics.fields(
                "provider", providerType,
                "adapter", adapterType,
                "stream", stream,
                "url", url,
                "body", diagnosticJson(body)));
    }

    public void responseStatus(int statusCode) {
        AiFoundationDiagnostics.trace("provider-response-status", invocationId,
            () -> AiFoundationDiagnostics.fields(
                "provider", providerType,
                "adapter", adapterType,
                "status", statusCode));
    }

    public void response(int statusCode, String body) {
        AiFoundationDiagnostics.trace("provider-response", invocationId,
            () -> AiFoundationDiagnostics.fields(
                "provider", providerType,
                "adapter", adapterType,
                "status", statusCode,
                "body", AiFoundationDiagnostics.redactSensitiveText(body)));
    }

    public void streamEvent(ProviderSseEvent event) {
        AiFoundationDiagnostics.trace("provider-stream-event", invocationId,
            () -> AiFoundationDiagnostics.fields(
                "provider", providerType,
                "adapter", adapterType,
                "event", event.event(),
                "id", event.id(),
                "data", AiFoundationDiagnostics.redactSensitiveText(event.data())));
    }

    private String diagnosticJson(Object value) {
        try {
            return AiFoundationDiagnostics.redactSensitiveText(
                OBJECT_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            return AiFoundationDiagnostics.redactSensitiveText(String.valueOf(value));
        }
    }
}
