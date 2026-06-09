package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatModel;
import run.halo.app.extension.Metadata;

class XiaomiMiMoProviderTest {

    private final XiaomiMiMoProvider providerType = new XiaomiMiMoProvider();

    @Test
    void metadata_matchesXiaomiMiMoProvider() {
        assertThat(providerType.getProviderType()).isEqualTo("mimo");
        assertThat(providerType.getDisplayName()).isEqualTo("Xiaomi MiMo");
        assertThat(providerType.getDescription()).isNotBlank();
        assertThat(providerType.getIconUrl())
            .isEqualTo("/plugins/ai-foundation/assets/static/brands/xiaomimimo.png");
        assertThat(providerType.getWebsiteUrl()).isEqualTo("https://platform.xiaomimimo.com/");
        assertThat(providerType.getDocumentationUrl())
            .isEqualTo("https://platform.xiaomimimo.com/#/docs/welcome");
        assertThat(providerType.isBuiltIn()).isTrue();
        assertThat(providerType.requiresBaseUrl()).isFalse();
        assertThat(providerType.getDefaultBaseUrl()).isEqualTo("https://api.xiaomimimo.com/v1");
    }

    @Test
    void supportsOnlyOpenAiChatAdapter() {
        assertThat(providerType.getSupportedAdapterTypes()).containsExactly(AdapterType.OPENAI_CHAT);
        assertThat(providerType.maxEmbeddingsPerCall()).isZero();
        assertThat(providerType.supportsParallelCalls()).isFalse();
        assertThat(providerType.buildEmbeddingModel(provider(null), "sk-test", "mimo-v2.5")).isNull();
    }

    @Test
    void iconAssetIsPackagedWithMainResources() {
        assertThat(getClass().getResource("/static/brands/xiaomimimo.png")).isNotNull();
    }

    @Test
    void buildChatModel_returnsOpenAiCompatibleChatModel() {
        var chatModel = providerType.buildChatModel(provider(null), "sk-test", "mimo-v2.5");

        assertThat(chatModel).isInstanceOf(OpenAiCompatibleChatModel.class);
    }

    @Test
    void resolveBaseUrl_usesCustomBaseUrlWhenProvided() {
        assertThat(providerType.resolveBaseUrl(provider("https://example.com")))
            .isEqualTo("https://example.com");
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("mimo-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("mimo");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
