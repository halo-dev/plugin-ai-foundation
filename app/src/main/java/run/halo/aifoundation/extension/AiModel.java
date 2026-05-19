package run.halo.aifoundation.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiModel",
    plural = "aimodels", singular = "aimodel")
public class AiModel extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private AiModelSpec spec;

    @Data
    public static class AiModelSpec {
        @Schema(requiredMode = REQUIRED, description = "References AiProvider.metadata.name (provider instance resource name)")
        private String providerName;
        @Schema(requiredMode = REQUIRED, description = "Unique identifier of the model within the provider")
        private String modelId;
        @Schema(requiredMode = REQUIRED, description = "Display name of the model")
        private String displayName;
        @Schema(requiredMode = REQUIRED, description = "Whether the model is enabled")
        private boolean enabled = true;
        @Schema(requiredMode = REQUIRED, description = "Primary model purpose")
        private ModelType modelType;
        @Schema(description = "Optional model features")
        private List<ModelFeature> features = List.of();
        @Schema(description = "Internal invocation adapter")
        private AdapterType adapterType;
        @Schema(description = "How this model profile was obtained")
        private DiscoverySource discoverySource = DiscoverySource.MANUAL;
        @Schema(description = "How reliable the discovered model profile is")
        private DiscoveryConfidence discoveryConfidence = DiscoveryConfidence.HIGH;
    }
}
