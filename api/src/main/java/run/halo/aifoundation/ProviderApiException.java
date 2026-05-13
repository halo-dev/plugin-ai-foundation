package run.halo.aifoundation;

public class ProviderApiException extends AiFoundationException {

    private final int statusCode;
    private final String providerType;

    public ProviderApiException(String providerType, int statusCode, String message) {
        super("Provider API error [" + providerType + "] HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
        this.providerType = providerType;
    }

    public ProviderApiException(String providerType, int statusCode, String message,
        Throwable cause) {
        super("Provider API error [" + providerType + "] HTTP " + statusCode + ": " + message,
            cause);
        this.statusCode = statusCode;
        this.providerType = providerType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getProviderType() {
        return providerType;
    }
}
