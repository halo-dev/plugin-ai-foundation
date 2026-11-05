package run.halo.aifoundation.provider.support;

import java.util.Map;
import java.util.Set;
import lombok.Builder;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.catalog.ModelCatalogMatch;
import run.halo.aifoundation.extension.ModelParameterProfile;

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
    ModelCapabilitySources capabilitySources,
    Map<ModelFeature, Boolean> featureEvidence,
    ModelCatalogMatch catalogMatch,
    ModelParameterProfile catalogParameters
) {
    public DiscoveredModel(String modelId, String displayName, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType, DiscoverySource source,
        DiscoveryConfidence confidence, ModelCapabilities capabilities,
        ModelCapabilitySources capabilitySources) {
        this(modelId, displayName, modelType, features, adapterType, source, confidence,
            capabilities, capabilitySources, Map.of(), null, null);
    }

    public DiscoveredModel(String modelId, String displayName, ModelType modelType,
        Set<ModelFeature> features, AdapterType adapterType, DiscoverySource source,
        DiscoveryConfidence confidence) {
        this(modelId, displayName, modelType, features, adapterType, source, confidence, null,
            ModelCapabilitySources.unknown(), Map.of(), null, null);
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
        featureEvidence = featureEvidence == null ? Map.of() : Map.copyOf(featureEvidence);
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