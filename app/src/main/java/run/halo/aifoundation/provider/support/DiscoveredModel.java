package run.halo.aifoundation.provider.support;

import java.util.Set;

public record DiscoveredModel(
    String modelId,
    String displayName,
    ModelType modelType,
    Set<ModelFeature> features,
    AdapterType adapterType,
    DiscoverySource source,
    DiscoveryConfidence confidence
) {
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
    }
}
