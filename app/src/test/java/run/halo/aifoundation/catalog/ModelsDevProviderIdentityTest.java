package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.zhipu.ZhiPuMessagesModel;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;

class ModelsDevProviderIdentityTest {

    @Test
    void chatPathOverridesOnlyExcludeChatCompletionsCatalogRoutes() throws Exception {
        var catalog = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"openai":{"id":"openai","models":{"model":{"id":"model","type":"chat",
              "api":{"chat-completions":{"base_url":"https://api.openai.com/v1"},
                     "responses":{"base_url":"https://api.openai.com/v1"}}}}}}
            """));
        var provider = provider(null);
        var type = new OpenAiProvider();
        var entry = catalog.find("openai", "model");
        assertThat(ModelsDevProviderIdentity.matches(provider, type, entry,
            AdapterType.OPENAI_CHAT))
            .isTrue();
        provider.getSpec().setChatEndpointPath("/tenant/chat/completions");
        assertThat(ModelsDevProviderIdentity.resolve(provider, type)).isEqualTo("openai");
        assertThat(ModelsDevProviderIdentity.matches(provider, type, entry,
            AdapterType.OPENAI_CHAT))
            .isFalse();
        assertThat(ModelsDevProviderIdentity.matches(provider, type, entry,
            AdapterType.OPENAI_RESPONSES))
            .isTrue();
        provider.getSpec().setBaseUrl("https://gateway.example/v1");
        assertThat(ModelsDevProviderIdentity.resolve(provider, type)).isNull();
    }

    @Test
    void matchesBundledNonChatModelsOnlyToTheirReviewedAdapterRoutes() {
        var bundle = ModelsDevBundledCatalog.load();
        assertThat(bundle).isNotNull();
        var openai = new OpenAiProvider();
        var provider = provider(null);
        var image = bundle.catalog().find("openai", "gpt-image-1-mini");
        assertThat(image).isNotNull();
        assertThat(ModelsDevProviderIdentity.matches(provider, openai, image,
            AdapterType.OPENAI_IMAGE)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(provider, openai, image,
            AdapterType.OPENAI_CHAT)).isFalse();
        assertThat(ModelsDevProviderIdentity.matches(provider, openai,
            bundle.catalog().find("openai", "text-embedding-3-large"),
            AdapterType.OPENAI_EMBEDDING)).isTrue();
        var openrouter = new OpenRouterProvider();
        assertThat(ModelsDevProviderIdentity.matches(provider, openrouter,
            bundle.catalog().find("openrouter", "black-forest-labs/flux-3-image"),
            AdapterType.OPENROUTER_IMAGE)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(provider, openrouter,
            bundle.catalog().find("openrouter", "amazon/titan-embed-image-v1"),
            AdapterType.OPENROUTER_EMBEDDING)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(provider, openrouter,
            bundle.catalog().find("openrouter", "cohere/rerank-4-fast"),
            AdapterType.RERANK)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(provider, new DashScopeProvider(),
            bundle.catalog().find("alibaba-cn", "qwen-image-2.0"),
            AdapterType.DASHSCOPE_IMAGE)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(provider, new ZhiPuProvider(),
            bundle.catalog().find("zhipuai", "glm-image"), AdapterType.ZHIPU_IMAGE)).isTrue();
        // This adapter uses /models/{model_path}/predictions, not the catalog's images API.
        assertThat(ModelsDevProviderIdentity.matches(provider, new AiHubMixProvider(),
            bundle.catalog().find("aihubmix", "gemini-2.5-flash-image"),
            AdapterType.AIHUBMIX_IMAGE)).isFalse();
    }

    @Test
    void rejectsNonChatProtocolPathAndEndpointMismatches() throws Exception {
        var provider = provider(null);
        var openrouter = new OpenRouterProvider();
        var root = new ObjectMapper().readTree("""
            {"openrouter":{"id":"openrouter","models":{
              "image":{"id":"image","type":"image","api":{"images":{
                "base_url":"https://openrouter.ai/api/v1","path":"/images/generations"}}},
              "embedding":{"id":"embedding","type":"embedding","api":{"embeddings":{
                "base_url":"https://openrouter.ai/api/v1","path":"/embeddings/multimodal"}}},
              "rerank":{"id":"rerank","type":"reranking","api":{"rerank":{
                "base_url":"https://another.example/v1","path":"/rerank"}}}
            }}}
            """);
        var catalog = ModelsDevCatalog.parse(root);
        assertThat(ModelsDevProviderIdentity.matches(provider, openrouter,
            catalog.find("openrouter", "image"), AdapterType.OPENROUTER_IMAGE)).isFalse();
        assertThat(ModelsDevProviderIdentity.matches(provider, openrouter,
            catalog.find("openrouter", "embedding"), AdapterType.OPENROUTER_EMBEDDING)).isFalse();
        assertThat(ModelsDevProviderIdentity.matches(provider, openrouter,
            catalog.find("openrouter", "rerank"), AdapterType.RERANK)).isFalse();
    }

    @Test
    void nativeDashScopeEmbeddingCannotBorrowACompatibleProtocolDeclaration() throws Exception {
        var catalog = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"alibaba-cn":{"id":"alibaba-cn","models":{
              "embedding":{"id":"embedding","type":"embedding","api":{"embeddings":{
                "base_url":"https://dashscope.aliyuncs.com/compatible-mode/v1"}}},
              "rerank":{"id":"rerank","type":"reranking","api":{"rerank":{
                "base_url":"https://dashscope.aliyuncs.com/compatible-mode/v1"}}}
            }}}
            """));
        var provider = provider(null);
        var type = new DashScopeProvider();
        assertThat(ModelsDevProviderIdentity.matches(provider, type,
            catalog.find("alibaba-cn", "embedding"), AdapterType.DASHSCOPE_EMBEDDING)).isFalse();
        assertThat(ModelsDevProviderIdentity.matches(provider, type,
            catalog.find("alibaba-cn", "rerank"), AdapterType.DASHSCOPE_NATIVE_RERANK)).isFalse();
        assertThat(ModelsDevProviderIdentity.matches(provider, type,
            catalog.find("alibaba-cn", "rerank"), AdapterType.DASHSCOPE_COMPATIBLE_RERANK))
            .isFalse();
    }

    @Test
    void distinguishesRegionsAndUsesExactDocumentedEndpoints() {
        assertThat(ModelsDevProviderIdentity.resolve(provider(null),
            new KimiProvider())).isEqualTo("moonshotai-cn");
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://api.moonshot.ai/v1"), new KimiProvider()))
            .isEqualTo("moonshotai");
        assertThat(ModelsDevProviderIdentity.resolve(provider(null),
            new DashScopeProvider())).isEqualTo("alibaba-cn");
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
            new DashScopeProvider())).isEqualTo("alibaba");
        assertThat(ModelsDevProviderIdentity.resolve(provider(null),
            new SiliconFlowProvider())).isEqualTo("siliconflow-cn");
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://api.siliconflow.com/v1"), new SiliconFlowProvider()))
            .isEqualTo("siliconflow");
    }

    @Test
    void excludesCodingPlansAndAmbiguousSharedEndpoints() {
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://coding.dashscope.aliyuncs.com/v1"),
            new DashScopeProvider())).isNull();
        // MiniMax's pay-as-you-go and coding-plan catalogs use the same endpoint. The current
        // provider configuration has no plan identity, so neither catalog is safe to infer.
        assertThat(ModelsDevProviderIdentity.resolve(provider(null),
            new MiniMaxProvider())).isNull();
    }

    @Test
    void acceptsOnlyCosmeticUrlNormalization() {
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://API.OPENAI.COM:443/v1/"), new OpenAiProvider()))
            .isEqualTo("openai");
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://api.openai.com:8443/v1"), new OpenAiProvider()))
            .isNull();
        assertThat(ModelsDevProviderIdentity.resolve(
            provider("https://user@api.openai.com/v1"), new OpenAiProvider()))
            .isNull();
    }

    @Test
    void usesReviewedMessagesRoutesForDeepSeekAndDashScope() throws Exception {
        var entry = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"provider":{"id":"provider","models":{"model":{"id":"model","type":"chat",
              "api":{"messages":{"base_url":"https://api.deepseek.com/anthropic/v1"}}}}}}
            """)).find("provider", "model");
        assertThat(ModelsDevProviderIdentity.matches(provider(null),
            new DeepSeekProvider(), entry, AdapterType.DEEPSEEK_MESSAGES)).isTrue();
        assertThat(ModelsDevProviderIdentity.matches(provider(null),
            new DeepSeekProvider(), entry, AdapterType.DEEPSEEK_CHAT)).isFalse();
        var dashscope = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"provider":{"id":"provider","models":{"model":{"id":"model","type":"chat",
              "api":{"messages":{"base_url":"https://dashscope.aliyuncs.com/apps/anthropic/v1"}}}}}}
            """)).find("provider", "model");
        assertThat(ModelsDevProviderIdentity.matches(provider(null),
            new DashScopeProvider(), dashscope, AdapterType.DASHSCOPE_MESSAGES)).isTrue();
    }

    @Test
    void unexpectedProtocolPathCannotMatchEvenWithTheCorrectBaseUrl() throws Exception {
        var entry = ModelsDevCatalog.parse(new ObjectMapper().readTree("""
            {"openai":{"id":"openai","models":{"model":{"id":"model","type":"chat",
              "api":{"responses":{"base_url":"https://api.openai.com/v1","path":"/private/responses"}}}}}}
            """)).find("openai", "model");
        assertThat(ModelsDevProviderIdentity.matches(provider(null),
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
            var provider = provider(region.getValue() + "///");
            provider.getSpec().setProviderType(type.getProviderType());
            assertThat(ModelsDevProviderIdentity.resolve(provider, type))
                .isEqualTo(region.getKey());
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
                bundle.catalog().find(otherRegion, "glm-4.7"), AdapterType.ZHIPU_MESSAGES))
                .isFalse();
        }
        assertThat(ModelsDevProviderIdentity.matches(provider(null), type,
            bundle.catalog().find("zhipuai", "glm-4.7"), AdapterType.ZHIPU_MESSAGES)).isTrue();
    }
}