package run.halo.aifoundation.provider.support;

import java.util.Set;

public record DiscoveredModel(
    String modelId,
    String displayName,
    Set<ModelCapability> capabilities
) {
    public DiscoveredModel {
        if (displayName == null || displayName.isBlank()) {
            displayName = modelId;
        }
    }
}
