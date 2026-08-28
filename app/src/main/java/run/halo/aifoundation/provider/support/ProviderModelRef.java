package run.halo.aifoundation.provider.support;

import java.util.Map;
import java.util.Objects;
import run.halo.aifoundation.extension.AiModel;

/**
 * Internal provider runtime configuration for the selected model.
 */
public record ProviderModelRef(
    String modelId,
    ModelType modelType,
    AdapterType adapterType,
    Map<String, Object> nativeOptions
) {

    public ProviderModelRef {
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(modelType, "modelType must not be null");
        nativeOptions = copyNativeOptions(nativeOptions);
    }

    public ProviderModelRef(String modelId, ModelType modelType, AdapterType adapterType) {
        this(modelId, modelType, adapterType, Map.of());
    }

    public static ProviderModelRef from(AiModel model) {
        Objects.requireNonNull(model, "model must not be null");
        var spec = Objects.requireNonNull(model.getSpec(), "model.spec must not be null");
        return new ProviderModelRef(spec.getModelId(), spec.getModelType(), spec.getAdapterType(),
            spec.getNativeOptions());
    }

    public ProviderModelRef withAdapterType(AdapterType resolvedAdapterType) {
        return new ProviderModelRef(modelId, modelType,
            Objects.requireNonNull(resolvedAdapterType, "adapterType must not be null"),
            nativeOptions);
    }

    String cacheSegment() {
        var adapter = adapterType == null ? "unresolved" : adapterType.getValue();
        return adapter + "/" + Integer.toUnsignedString(nativeOptions.hashCode(), 36);
    }

    private static Map<String, Object> copyNativeOptions(Map<String, Object> nativeOptions) {
        if (nativeOptions == null) {
            return Map.of();
        }
        NativeModelOptionsValidator.validate(nativeOptions);
        return Map.copyOf(nativeOptions);
    }
}
