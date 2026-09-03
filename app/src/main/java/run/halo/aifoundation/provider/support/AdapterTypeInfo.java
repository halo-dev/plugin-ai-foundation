package run.halo.aifoundation.provider.support;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** User-facing metadata for one provider-owned invocation adapter. */
@Data
@Builder
public class AdapterTypeInfo {

    private AdapterType adapterType;
    private ModelType modelType;
    private String displayName;
    private String description;
    private List<ModelFeature> supportedFeatures;
    @Schema(description = "Adapter-specific parameter mapping defaults that override provider defaults")
    private Map<String, DefaultParameterMappingInfo> defaultParameterMappingOverrides;
    private boolean recommended;
}
