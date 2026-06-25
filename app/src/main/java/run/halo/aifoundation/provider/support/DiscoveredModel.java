package run.halo.aifoundation.provider.support;

import java.util.Set;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;

public record DiscoveredModel(
    String modelId,
    String displayName,
    ModelType modelType,
    Set<ModelFeature> features,
    AdapterType adapterType,
    DiscoverySource source,
    DiscoveryConfidence confidence,
    ModelCapabilities capabilities,
    ModelCapabilitySources capabilitySources
) {
    public DiscoveredModel(String modelId, String displayName, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType, DiscoverySource source,
        DiscoveryConfidence confidence) {
        this(modelId, displayName, modelType, features, adapterType, source, confidence, null,
            ModelCapabilitySources.unknown());
    }

    public DiscoveredModel {
        if (displayName == null || displayName.isBlank()) {
            displayName = modelId;
        }
        if (features == null) {
            features = Set.of();
        }
        if (source == null) {
            source = DiscoverySource.RULE;
        }
        if (confidence == null) {
            confidence = DiscoveryConfidence.LOW;
        }
        if (capabilitySources == null) {
            capabilitySources = ModelCapabilitySources.unknown();
        }
    }
}
