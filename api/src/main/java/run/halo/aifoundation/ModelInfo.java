package run.halo.aifoundation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {
    /** The AiModel.metadata.name (model resource name). */
    private String name;
    /** The AiProvider.metadata.name (provider instance resource name). */
    private String providerName;
    private String modelId;
    private String displayName;
    private boolean enabled;
}
