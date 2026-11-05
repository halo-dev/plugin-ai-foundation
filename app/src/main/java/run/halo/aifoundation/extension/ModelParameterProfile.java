package run.halo.aifoundation.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;

/** A model/adapter-bound configuration, separate from administrator overrides. */
@Data
public class ModelParameterProfile {
    private String providerType;
    @Schema(description = "Provider endpoint captured with the catalog configuration")
    private String providerBaseUrl;
    private String modelId;
    private AdapterType adapterType;
    @Schema(description = "Automatically derived or resolved parameter mappings")
    private ModelParameterMappings mappings;
    private ModelParameterConstraints constraints;
    @Schema(description = "Explicit provider feature evidence, excluding catalog facts; "
        + "empty when no provider evidence was found")
    private Map<ModelFeature, Boolean> featureEvidence = Map.of();
}