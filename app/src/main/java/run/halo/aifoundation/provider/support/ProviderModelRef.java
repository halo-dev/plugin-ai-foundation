package run.halo.aifoundation.provider.support;

import java.util.Objects;
import run.halo.aifoundation.extension.AiModel;

/**
 * Provider-neutral identity of the configured model transport selected for an invocation.
 */
public record ProviderModelRef(
    String modelId,
    ModelType modelType,
    AdapterType adapterType
) {

    public ProviderModelRef {
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(modelType, "modelType must not be null");
    }

    public static ProviderModelRef from(AiModel model) {
        Objects.requireNonNull(model, "model must not be null");
        var spec = Objects.requireNonNull(model.getSpec(), "model.spec must not be null");
        return new ProviderModelRef(spec.getModelId(), spec.getModelType(), spec.getAdapterType());
    }

    public ProviderModelRef withAdapterType(AdapterType resolvedAdapterType) {
        return new ProviderModelRef(modelId, modelType,
            Objects.requireNonNull(resolvedAdapterType, "adapterType must not be null"));
    }

    String cacheSegment() {
        return adapterType == null ? "unresolved" : adapterType.getValue();
    }
}
