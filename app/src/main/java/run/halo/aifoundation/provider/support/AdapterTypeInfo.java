package run.halo.aifoundation.provider.support;

import java.util.List;
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
    private boolean recommended;
}
