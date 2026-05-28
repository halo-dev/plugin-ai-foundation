package run.halo.aifoundation.service.model;

import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;

public record ModelResolution(
    AiModel model,
    AiProvider provider,
    AiProviderType providerType,
    String apiKey
) {

    public String providerTypeName() {
        return provider.getSpec().getProviderType();
    }

    public String modelId() {
        return model.getSpec().getModelId();
    }
}
