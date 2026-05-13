package run.halo.aifoundation.extension;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiModel",
    plural = "aimodels", singular = "aimodel")
public class AiModel extends AbstractExtension {

    private AiModelSpec spec;

    @Data
    public static class AiModelSpec {
        /** References AiProvider.metadata.name (provider instance resource name). */
        private String providerName;
        private String modelId;
        private String displayName;
        private boolean enabled = true;
        /** Model group for display/filtering purposes. */
        private String group;
        /** Capability labels (e.g., chat, embedding, vision). */
        private List<String> capabilities;
        /** Endpoint type (e.g., openai-chat, openai-embedding, ollama-chat). */
        private String endpointType;
        /** Whether the model supports streaming text delta. */
        private Boolean supportedTextDelta;
    }
}
