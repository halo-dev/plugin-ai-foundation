package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelOption {

    @Schema(description = "AiModel.metadata.name")
    private String name;

    @Schema(description = "Provider-side model identifier")
    private String modelId;

    @Schema(description = "Display name of the model")
    private String displayName;

    @Schema(description = "Primary model purpose")
    private ModelType modelType;

    @Schema(description = "Optional model features")
    private List<ModelFeature> features;

    @Schema(description = "Whether the AiModel is enabled")
    private boolean enabled;

    @Schema(description = "Whether this option is selectable based on local configuration state")
    private boolean available;

    @Nullable
    @Schema(description = "Reason the option is not selectable")
    private ModelOptionUnavailableReason unavailableReason;

    @Schema(description = "Provider display summary")
    private ModelOptionProvider provider;
}
