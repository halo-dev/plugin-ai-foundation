package run.halo.aifoundation.endpoint;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelOptionProvider {

    @Schema(description = "AiProvider.metadata.name")
    private String name;

    @Schema(description = "Display name of the provider")
    private String displayName;

    @Nullable
    @Schema(description = "AiProvider.spec.providerType")
    private String providerType;

    @Nullable
    @Schema(description = "Display name of the provider type")
    private String providerTypeDisplayName;

    @Nullable
    @Schema(description = "Provider type icon URL")
    private String iconUrl;

    @Schema(description = "Whether the AiProvider is enabled")
    private boolean enabled;

    @Nullable
    @Schema(description = "Provider diagnostic phase")
    private String phase;

    @Nullable
    @Schema(description = "Provider diagnostic last checked time")
    private String lastCheckedAt;
}
