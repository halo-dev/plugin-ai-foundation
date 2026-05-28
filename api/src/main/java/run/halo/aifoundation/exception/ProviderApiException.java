package run.halo.aifoundation.exception;

/**
 * Raised when a provider HTTP API returns a failed response.
 */
public class ProviderApiException extends AiFoundationException {

    /**
     * HTTP status code returned by the provider, or {@code 0} when unavailable.
     */
    private final int statusCode;
    /**
     * Provider type id associated with the failed request.
     */
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
