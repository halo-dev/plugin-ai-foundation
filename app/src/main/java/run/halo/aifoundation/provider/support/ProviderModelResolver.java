package run.halo.aifoundation.provider.support;

import java.util.Objects;
import run.halo.aifoundation.provider.AiProviderType;

/** Resolves a persisted model adapter against the authoritative provider type metadata. */
public final class ProviderModelResolver {

    private ProviderModelResolver() {
    }

    public static ProviderModelRef resolve(AiProviderType providerType, ProviderModelRef model) {
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(model, "model must not be null");
        var adapterType = model.adapterType();
        if (adapterType != null && providerType.getSupportedAdapterTypes().contains(adapterType)) {
            if (adapterType.getModelType() != model.modelType()) {
                throw new IllegalArgumentException("Adapter type '" + adapterType.getValue()
                    + "' cannot be used with model type '" + model.modelType().getValue() + "'");
            }
            return model;
        }
        if (adapterType == null || isLegacyGenericAdapter(providerType, adapterType,
            model.modelType())) {
            return providerType.recommendAdapterType(model.modelType())
                .map(model::withAdapterType)
                .orElseThrow(() -> new IllegalArgumentException(
                    "No adapter is available for model type '" + model.modelType().getValue()
                        + "' on provider type '" + providerType.getProviderType() + "'"));
        }
        throw new IllegalArgumentException("Adapter type '" + adapterType.getValue()
            + "' is not supported by provider type '" + providerType.getProviderType() + "'");
    }

    private static boolean isLegacyGenericAdapter(AiProviderType providerType,
        AdapterType adapterType, ModelType modelType) {
        if (!providerType.isBuiltIn()) {
            return false;
        }
        return switch (modelType) {
            case LANGUAGE -> adapterType == AdapterType.OPENAI_CHAT;
            case EMBEDDING -> adapterType == AdapterType.OPENAI_EMBEDDING;
            case IMAGE_GENERATION -> adapterType == AdapterType.OPENAI_IMAGE;
            default -> false;
        };
    }
}
