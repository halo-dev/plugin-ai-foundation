package run.halo.aifoundation.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "Model group for display/filtering purposes")
        private String group;
        @Schema(description = "Capability labels (e.g., chat, embedding, vision)")
        private List<String> capabilities;
        @Schema(requiredMode = REQUIRED, description = "Endpoint type (e.g., openai-chat, openai-embedding, ollama-chat)")
        private String endpointType;
        @Schema(description = "Whether the model supports streaming text delta")
        private boolean supportedTextDelta = true;
    }
}
