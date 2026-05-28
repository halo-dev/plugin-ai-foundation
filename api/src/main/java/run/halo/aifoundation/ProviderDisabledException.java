package run.halo.aifoundation;

/**
 * Raised when a model references a provider resource that is disabled.
 */
public class ProviderDisabledException extends AiFoundationException {

    public ProviderDisabledException(String providerName) {
        super("AI provider is disabled: " + providerName);
    }
}
