package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.Metadata;

class AbstractAiProviderTypeTest {

    static class TestProviderType extends AbstractAiProviderType {
        @Override
        public String getProviderType() {
            return "test";
        }

        @Override
        public String getDisplayName() {
            return "Test";
        }

        @Override
        public boolean isBuiltIn() {
            return true;
        }

        @Override
        public boolean requiresBaseUrl() {
            return false;
        }

        @Override
        public String getDefaultBaseUrl() {
            return "https://test.example.com";
        }

        @Override
        public java.util.List<String> getSupportedEndpointTypes() {
            return java.util.List.of("openai-chat");
        }

        @Override
        public boolean supportsEmbeddings() {
            return false;
        }

        @Override
        public org.springframework.ai.chat.model.ChatModel buildChatModel(
            AiProvider provider, String apiKey, String modelId) {
            return null;
        }
    }

    @Test
    void resolveBaseUrl_usesDefaultWhenSpecBaseUrlIsNull() {
        var provider = providerWithBaseUrl(null);
        var type = new TestProviderType();
        assertThat(type.resolveBaseUrl(provider)).isEqualTo("https://test.example.com");
    }

    @Test
    void resolveBaseUrl_usesDefaultWhenSpecBaseUrlIsBlank() {
        var provider = providerWithBaseUrl("  ");
        var type = new TestProviderType();
        assertThat(type.resolveBaseUrl(provider)).isEqualTo("https://test.example.com");
    }

    @Test
    void resolveBaseUrl_usesSpecBaseUrlWhenProvided() {
        var provider = providerWithBaseUrl("https://custom.example.com");
        var type = new TestProviderType();
        assertThat(type.resolveBaseUrl(provider)).isEqualTo("https://custom.example.com");
    }

    @Test
    void defaults_maxEmbeddingsPerCall_is96() {
        assertThat(new TestProviderType().maxEmbeddingsPerCall()).isEqualTo(96);
    }

    @Test
    void defaults_supportsParallelCalls_isTrue() {
        assertThat(new TestProviderType().supportsParallelCalls()).isTrue();
    }

    @Test
    void defaults_buildEmbeddingModel_returnsNull() {
        var provider = providerWithBaseUrl(null);
        assertThat(new TestProviderType().buildEmbeddingModel(provider, "key", "model")).isNull();
    }

    private AiProvider providerWithBaseUrl(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("test-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
