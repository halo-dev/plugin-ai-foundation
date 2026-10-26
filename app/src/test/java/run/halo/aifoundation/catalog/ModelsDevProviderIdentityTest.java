package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;

class ModelsDevProviderIdentityTest {

    @Test
    void distinguishesRegionsAndUsesExactDocumentedEndpoints() {
        assertThat(ModelsDevProviderIdentity.resolve(ModelCatalogEnricherTest.provider(null),
            new KimiProvider())).isEqualTo("moonshotai-cn");
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://api.moonshot.ai/v1"), new KimiProvider()))
            .isEqualTo("moonshotai");
        assertThat(ModelsDevProviderIdentity.resolve(ModelCatalogEnricherTest.provider(null),
            new DashScopeProvider())).isEqualTo("alibaba-cn");
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
            new DashScopeProvider())).isEqualTo("alibaba");
        assertThat(ModelsDevProviderIdentity.resolve(ModelCatalogEnricherTest.provider(null),
            new SiliconFlowProvider())).isEqualTo("siliconflow-cn");
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://api.siliconflow.com/v1"), new SiliconFlowProvider()))
            .isEqualTo("siliconflow");
    }

    @Test
    void excludesCodingPlansAndAmbiguousSharedEndpoints() {
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://coding.dashscope.aliyuncs.com/v1"),
            new DashScopeProvider())).isNull();
        // MiniMax's pay-as-you-go and coding-plan catalogs use the same endpoint. The current
        // provider configuration has no plan identity, so neither catalog is safe to infer.
        assertThat(ModelsDevProviderIdentity.resolve(ModelCatalogEnricherTest.provider(null),
            new MiniMaxProvider())).isNull();
    }

    @Test
    void acceptsOnlyCosmeticUrlNormalization() {
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://API.OPENAI.COM:443/v1/"), new OpenAiProvider()))
            .isEqualTo("openai");
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://api.openai.com:8443/v1"), new OpenAiProvider()))
            .isNull();
        assertThat(ModelsDevProviderIdentity.resolve(
            ModelCatalogEnricherTest.provider("https://user@api.openai.com/v1"), new OpenAiProvider()))
            .isNull();
    }
}
