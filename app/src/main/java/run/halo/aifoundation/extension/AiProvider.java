package run.halo.aifoundation.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "aifoundation.halo.run", version = "v1alpha1", kind = "AiProvider",
    plural = "aiproviders", singular = "aiprovider")
public class AiProvider extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private AiProviderSpec spec;
    private AiProviderStatus status;

    @Data
    public static class AiProviderSpec {
        @Schema(requiredMode = REQUIRED, description = "Provider type: aihubmix, openai, deepseek, siliconflow, doubao, ernie, zhipuai, ollama, openailike, dashscope")
        private String providerType;
        @Schema(requiredMode = REQUIRED, description = "Display name of the provider")
        private String displayName;
        @Schema(requiredMode = REQUIRED, description = "Whether the provider is enabled")
        private boolean enabled = true;
        @Schema(description = "Provider-documented API base URL. Leave blank to use the provider default.")
        private String baseUrl;
        @Schema(description = "Chat endpoint path relative to baseUrl. OpenAI-compatible providers only.")
        private String chatEndpointPath;
        @Schema(description = "Embedding endpoint path relative to baseUrl. OpenAI-compatible providers only.")
        private String embeddingEndpointPath;
        @Schema(description = "Rerank endpoint path relative to baseUrl. OpenAI-compatible providers only.")
        private String rerankEndpointPath;
        @Schema(description = "Image generation endpoint path relative to baseUrl. OpenAI-compatible providers only.")
        private String imageEndpointPath;
        @Schema(description = "Name of the Halo Secret containing the API key")
        private String apiKeySecretName;
        @Schema(description = "Proxy host for this provider (optional)")
        private String proxyHost;
        @Schema(description = "Proxy port for this provider (optional)")
        private Integer proxyPort;
    }

    @Data
    public static class AiProviderStatus {
        @Schema(description = "Current phase of the provider")
        private Phase phase = Phase.UNKNOWN;
        @Schema(description = "Status message, especially useful when phase is ERROR")
        private String message;
        @Schema(description = "Timestamp of the last connectivity check")
        private Instant lastCheckedAt;

        public enum Phase {
            UNKNOWN, OK, ERROR
        }
    }
}
