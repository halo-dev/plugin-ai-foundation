package run.halo.aifoundation.provider.support;

import java.util.Set;
import lombok.Builder;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;

@Builder(toBuilder = true)
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
        displayName = displayNameOrModelId(displayName, modelId);
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

    private static String displayNameOrModelId(String displayName, String modelId) {
        if (displayName == null) {
            return modelId;
        }
        if (displayName.isBlank()) {
            return modelId;
        }
        return displayName;
    }
}
