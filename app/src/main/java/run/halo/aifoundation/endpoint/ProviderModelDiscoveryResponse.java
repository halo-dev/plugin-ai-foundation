package run.halo.aifoundation.endpoint;

import java.util.List;
import run.halo.aifoundation.catalog.ModelCatalogStatus;

public record ProviderModelDiscoveryResponse(
    String providerName,
    List<DiscoveredModelItem> models,
    ModelCatalogStatus catalog
) {
}
