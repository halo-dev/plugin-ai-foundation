package run.halo.aifoundation.endpoint;

import java.util.List;

public record ProviderModelDiscoveryResponse(
    String providerName,
    List<DiscoveredModelItem> models
) {
}
