package run.halo.aifoundation;

public class ProviderDisabledException extends AiFoundationException {

    public ProviderDisabledException(String providerName) {
        super("AI provider is disabled: " + providerName);
    }
}
