package run.halo.aifoundation.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.aifoundation.provider.diagnostics.ProviderFailureCategory;
import run.halo.aifoundation.provider.diagnostics.ProviderHealthCheck;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "aifoundation.halo.run",
        version = "v1alpha1",
        kind = "AiProvider",
        plural = "aiproviders",
        singular = "aiprovider")
public class AiProvider extends AbstractExtension {

    @Schema(requiredMode = REQUIRED)
    private AiProviderSpec spec;

    private AiProviderStatus status;

    @Data
    public static class AiProviderSpec {
        @Schema(
                requiredMode = REQUIRED,
                description =
                        "Provider type: aihubmix, openai, deepseek, siliconflow, doubao, ernie, zhipuai, ollama, openailike, dashscope")
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

        @Schema(
                description =
                        "In-memory buffer limit in bytes for image generation responses (optional, defaults to 64 MB)")
        private Integer maxInMemorySize;

        @Schema(description = "Administrator overrides for provider-neutral parameter mappings")
        private ModelParameterMappings parameterMappings;
    }

    @Data
    public static class AiProviderStatus {
        @Schema(description = "Current phase of the provider")
        private Phase phase = Phase.UNKNOWN;

        @Schema(description = "Status message, especially useful when phase is ERROR")
        private String message;

        @Schema(description = "Timestamp of the last connectivity check")
        private Instant lastCheckedAt;

        @Schema(description = "Failure category of the primary failed check, or null when no check failed")
        private ProviderFailureCategory failureCategory;

        @Schema(description = "Per-target results of the latest connectivity check")
        private List<ProviderHealthCheck> checks = List.of();

        public enum Phase {
            UNKNOWN,
            OK,
            ERROR
        }
    }
}
