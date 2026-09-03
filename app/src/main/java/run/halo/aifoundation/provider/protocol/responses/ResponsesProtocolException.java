package run.halo.aifoundation.provider.protocol.responses;

import java.util.Map;
import run.halo.aifoundation.diagnostics.AiFoundationDiagnostics;

public final class ResponsesProtocolException extends IllegalStateException {

    private final String eventType;
    private final Map<String, Object> providerMetadata;

    ResponsesProtocolException(String providerType, String eventType, String message,
        Map<String, Object> providerMetadata) {
        super(providerType + " Responses stream failed at " + eventType + ": "
            + AiFoundationDiagnostics.redactSensitiveText(message));
        this.eventType = eventType;
        this.providerMetadata = Map.copyOf(providerMetadata);
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getProviderMetadata() {
        return providerMetadata;
    }
}
