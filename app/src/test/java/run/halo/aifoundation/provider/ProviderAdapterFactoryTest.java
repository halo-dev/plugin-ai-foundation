package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.Metadata;

class ProviderAdapterFactoryTest {

    @ParameterizedTest
    @CsvSource({
        "openai, OpenAiAdapter",
        "aihubmix, AiHubMixAdapter",
        "deepseek, DeepSeekAdapter",
        "siliconflow, SiliconFlowAdapter",
        "doubao, DouBaoAdapter",
        "ernie, ErnieAdapter",
        "zhipuai, ZhiPuAdapter",
        "ollama, OllamaAdapter",
        "openailike, OpenAiLikeAdapter",
    })
    void create_knownType_returnsCorrectAdapterClass(String providerType, String adapterClassName)
        throws ClassNotFoundException {
        var provider = providerWithType(providerType);
        var adapter = ProviderAdapterFactory.create(provider, "test-key");

        var expectedClass = Class.forName(
            "run.halo.aifoundation.provider." + adapterClassName);
        assertThat(adapter).isInstanceOf(expectedClass);
    }

    @ParameterizedTest
    @CsvSource({"OPENAI", "OpenAI", "DeepSeek", "OLLAMA"})
    void create_caseInsensitiveType_returnsAdapter(String providerType) {
        var provider = providerWithType(providerType);
        var adapter = ProviderAdapterFactory.create(provider, "test-key");
        assertThat(adapter).isNotNull();
    }

    @Test
    void create_unknownType_throwsIllegalArgumentException() {
        var provider = providerWithType("unknown-provider");

        assertThatThrownBy(() -> ProviderAdapterFactory.create(provider, "key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported provider type: unknown-provider");
    }

    @Test
    void create_nullProviderType_throwsIllegalArgumentException() {
        var provider = providerWithType(null);

        assertThatThrownBy(() -> ProviderAdapterFactory.create(provider, "key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Provider type is null");
    }

    private AiProvider providerWithType(String providerType) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("test-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setBaseUrl("http://localhost");
        provider.setSpec(spec);
        return provider;
    }
}
