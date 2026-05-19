package run.halo.aifoundation.endpoint;

import java.util.List;
import org.springframework.lang.Nullable;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

public record DiscoveredModelItem(
    String modelId,
    String displayName,
    String name,
    ModelType modelType,
    List<ModelFeature> features,
    DiscoverySource source,
    DiscoveryConfidence confidence,
    @Nullable
    AdapterType adapterType
) {
}
