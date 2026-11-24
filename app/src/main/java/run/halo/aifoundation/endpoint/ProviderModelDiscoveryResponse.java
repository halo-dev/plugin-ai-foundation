package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import run.halo.aifoundation.catalog.ModelCatalogStatus;

@Schema(description = "Remote model discovery result with saved-model sync state.")
public record ProviderModelDiscoveryResponse(
        String providerName,
        List<DiscoveredModelItem> models,
        List<MissingRemoteModelItem> missingModels,
        ModelCatalogStatus catalog) {}
