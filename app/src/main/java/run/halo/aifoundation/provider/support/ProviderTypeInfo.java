package run.halo.aifoundation.provider.support;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
@Builder
public class ProviderTypeInfo {

    private String providerType;
    private String displayName;
    @Nullable
    private String description;
    @Nullable
    private String iconUrl;
    @Nullable
    private String documentationUrl;
    @Nullable
    private String websiteUrl;
    private boolean builtIn;
    private boolean requiresBaseUrl;
    @Nullable
    private String defaultBaseUrl;
    @Nullable
    private String completionsPath;
    private List<ModelType> supportedModelTypes;
    private List<ModelFeature> supportedFeatures;
    private List<AdapterType> supportedAdapterTypes;
}
