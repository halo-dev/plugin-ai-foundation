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
        @Schema(requiredMode = REQUIRED, description = "Provider type: aihubmix, openai, deepseek, siliconflow, doubao, ernie, zhipuai, ollama, openailike")
        private String providerType;
        @Schema(requiredMode = REQUIRED, description = "Display name of the provider")
        private String displayName;
        @Schema(requiredMode = REQUIRED, description = "Whether the provider is enabled")
        private boolean enabled = true;
        @Schema(description = "Base URL. Required for ollama and openailike; built-in providers use defaults")
        private String baseUrl;
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
