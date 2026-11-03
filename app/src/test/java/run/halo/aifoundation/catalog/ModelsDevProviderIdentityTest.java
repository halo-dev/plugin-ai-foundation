package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.zhipu.ZhiPuMessagesModel;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;

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

    @Test
    void usesReviewedMessagesRoutesForDeepSeekAndDashScope() throws Exception {
        var entry = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"provider":{"id":"provider","models":{"model":{"id":"model","type":"chat",
              "api":{"messages":{"base_url":"https://api.deepseek.com/anthropic/v1"}}}}}}
            """)).find("provider", "model");
        assertThat(ModelsDevProviderIdentity.matches(ModelCatalogEnricherTest.provider(null),
            new DeepSeekProvider(), entry, AdapterType.DEEPSEEK_MESSAGES)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(ModelCatalogEnricherTest.provider(null),
            new DeepSeekProvider(), entry, AdapterType.DEEPSEEK_CHAT)).isFalse();
        var dashscope = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"provider":{"id":"provider","models":{"model":{"id":"model","type":"chat",
              "api":{"messages":{"base_url":"https://dashscope.aliyuncs.com/apps/anthropic/v1"}}}}}}
            """)).find("provider", "model");
        assertThat(ModelsDevProviderIdentity.matches(ModelCatalogEnricherTest.provider(null),
            new DashScopeProvider(), dashscope, AdapterType.DASHSCOPE_MESSAGES)).isTrue();
    }

    @Test
    void unexpectedProtocolPathCannotMatchEvenWithTheCorrectBaseUrl() throws Exception {
        var entry = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"openai":{"id":"openai","models":{"model":{"id":"model","type":"chat",
              "api":{"responses":{"base_url":"https://api.openai.com/v1","path":"/private/responses"}}}}}}
            """)).find("openai", "model");
        assertThat(ModelsDevProviderIdentity.matches(ModelCatalogEnricherTest.provider(null),
            new OpenAiProvider(), entry, AdapterType.OPENAI_RESPONSES)).isFalse();
    }

    @Test
    void bundledZhipuMessagesMatchTheActualAdapterRouteInBothRegions() {
        var bundle = ModelsDevBundledCatalog.load();
        assertThat(bundle).isNotNull();
        var type = new ZhiPuProvider();
        var endpoints = Map.of("zhipuai", "https://open.bigmodel.cn/api/paas/v4",
            "zai", "https://api.z.ai/api/paas/v4");
        for (var region : endpoints.entrySet()) {
            var entry = bundle.catalog().find(region.getKey(), "glm-4.7");
            assertThat(entry).isNotNull();
            var provider = ModelCatalogEnricherTest.provider(region.getValue() + "///");
            provider.getSpec().setProviderType(type.getProviderType());
            assertThat(ModelsDevProviderIdentity.resolve(provider, type)).isEqualTo(region.getKey());
            assertThat(ModelsDevProviderIdentity.matches(provider, type, entry,
                AdapterType.ZHIPU_MESSAGES)).isTrue();
            assertThat(ModelsDevProviderIdentity.matches(provider, type, entry,
                AdapterType.ZHIPU_CHAT)).isTrue();

            var messages = (ZhiPuMessagesModel) type.buildChatModel(provider, "key",
                new ProviderModelRef("glm-4.7", ModelType.LANGUAGE, AdapterType.ZHIPU_MESSAGES));
            var endpoint = (String) ReflectionTestUtils.invokeMethod(messages, "endpoint",
                messages.getOptions());
            assertThat(endpoint).isEqualTo(entry.api("messages").path("base_url").asText()
                + "/messages");

            var otherRegion = region.getKey().equals("zhipuai") ? "zai" : "zhipuai";
            assertThat(ModelsDevProviderIdentity.matches(provider, type,
                bundle.catalog().find(otherRegion, "glm-4.7"), AdapterType.ZHIPU_MESSAGES)).isFalse();
        }
        assertThat(ModelsDevProviderIdentity.matches(ModelCatalogEnricherTest.provider(null), type,
            bundle.catalog().find("zhipuai", "glm-4.7"), AdapterType.ZHIPU_MESSAGES)).isTrue();
    }
}