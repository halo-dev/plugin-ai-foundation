package run.halo.aifoundation.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        /** Provider type: aihubmix, openai, deepseek, siliconflow, doubao, ernie, zhipuai, ollama, openailike */
        @Schema(requiredMode = REQUIRED)
        private String providerType;
        @Schema(requiredMode = REQUIRED)
        private String displayName;
        @Schema(requiredMode = REQUIRED)
        private boolean enabled = true;
        /** Base URL. Required for ollama and openailike; built-in providers use defaults. */
        private String baseUrl;
        /** Name of the Halo Secret containing the API key. */
        private String apiKeySecretName;
        /** Proxy host for this provider (optional). */
        private String proxyHost;
        /** Proxy port for this provider (optional). */
        private Integer proxyPort;
        /** Provider-specific advanced configuration. */
        private Map<String, String> config;
    }

    @Data
    public static class AiProviderStatus {
        private Phase phase = Phase.UNKNOWN;
        private String message;
        private Instant lastCheckedAt;

        public enum Phase {
            UNKNOWN, OK, ERROR
        }
    }

    /** Supported provider types. */
    public static final List<String> SUPPORTED_PROVIDER_TYPES = List.of(
        "aihubmix", "openai", "deepseek", "siliconflow",
        "doubao", "ernie", "zhipuai", "ollama", "openailike"
    );
}
