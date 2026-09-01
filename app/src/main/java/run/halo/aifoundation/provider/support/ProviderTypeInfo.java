package run.halo.aifoundation.provider.support;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
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
    @Nullable
    private String chatEndpointPath;
    @Nullable
    private String embeddingEndpointPath;
    @Nullable
    private String rerankEndpointPath;
    @Nullable
    private String imageEndpointPath;
    private List<ModelType> supportedModelTypes;
    private List<ModelFeature> supportedFeatures;
    private List<AdapterType> supportedAdapterTypes;
    @Schema(description = "Provider-owned invocation adapters available for model configuration")
    private List<AdapterTypeInfo> adapters;
    private List<ModelParameterDefinitionInfo> parameterDefinitions;
    private List<ParameterMappingTemplateInfo> parameterMappingTemplates;
    private Map<String, DefaultParameterMappingInfo> defaultParameterMappings;
}
