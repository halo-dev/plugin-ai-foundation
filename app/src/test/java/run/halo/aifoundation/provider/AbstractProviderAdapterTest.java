package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.Metadata;

class AbstractProviderAdapterTest {

    /**
     * Minimal concrete subclass for testing AbstractProviderAdapter behaviour.
     */
    static class TestAdapter extends AbstractProviderAdapter {
        TestAdapter(AiProvider provider, String apiKey) {
            super(provider, apiKey);
        }

        @Override
        public org.springframework.ai.chat.model.ChatModel buildChatModel(String modelId) {
            return mock(org.springframework.ai.chat.model.ChatModel.class);
        }

        @Override
        public String getProviderType() {
            return "test";
        }

        @Override
        protected String getDefaultBaseUrl() {
            return "https://test.example.com";
        }
    }

    @Test
    void resolveBaseUrl_usesDefaultWhenSpecBaseUrlIsNull() {
        var provider = providerWithBaseUrl(null);
        var adapter = new TestAdapter(provider, "key");

        assertThat(adapter.resolveBaseUrl("https://default.example.com"))
            .isEqualTo("https://default.example.com");
    }

    @Test
    void resolveBaseUrl_usesDefaultWhenSpecBaseUrlIsBlank() {
        var provider = providerWithBaseUrl("  ");
        var adapter = new TestAdapter(provider, "key");

        assertThat(adapter.resolveBaseUrl("https://default.example.com"))
            .isEqualTo("https://default.example.com");
    }

    @Test
    void resolveBaseUrl_usesSpecBaseUrlWhenProvided() {
        var provider = providerWithBaseUrl("https://custom.example.com");
        var adapter = new TestAdapter(provider, "key");

        assertThat(adapter.resolveBaseUrl("https://default.example.com"))
            .isEqualTo("https://custom.example.com");
    }

    @Test
    void defaults_maxEmbeddingsPerCall_is96() {
        var adapter = new TestAdapter(providerWithBaseUrl(null), "key");
        assertThat(adapter.maxEmbeddingsPerCall()).isEqualTo(96);
    }

    @Test
    void defaults_supportsParallelCalls_isTrue() {
        var adapter = new TestAdapter(providerWithBaseUrl(null), "key");
        assertThat(adapter.supportsParallelCalls()).isTrue();
    }

    @Test
    void defaults_buildEmbeddingModel_returnsNull() {
        var adapter = new TestAdapter(providerWithBaseUrl(null), "key");
        assertThat(adapter.buildEmbeddingModel("any-model")).isNull();
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
