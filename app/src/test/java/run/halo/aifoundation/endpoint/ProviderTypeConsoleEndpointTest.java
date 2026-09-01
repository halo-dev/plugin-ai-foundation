package run.halo.aifoundation.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;
import run.halo.aifoundation.provider.ernie.ErnieProvider;
import run.halo.aifoundation.provider.gitee.GiteeProvider;
import run.halo.aifoundation.provider.ollama.OllamaProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.mimo.MiMoProvider;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.mapping.ModelParameterCatalog;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;

class ProviderTypeConsoleEndpointTest {

    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private final WebTestClient webTestClient = WebTestClient
        .bindToRouterFunction(new ProviderTypeConsoleEndpoint(providerClientCache,
            new ParameterMappingTemplateRegistry(), new ModelParameterCatalog()).endpoint())
        .configureClient()
        .build();

    @Test
    void listProviderTypes_includesXiaomiMiMoMetadata() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("mimo", new MiMoProvider()));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].providerType").isEqualTo("mimo")
            .jsonPath("$[0].displayName").isEqualTo("Xiaomi MiMo")
            .jsonPath("$[0].description").isNotEmpty()
            .jsonPath("$[0].iconUrl")
            .isEqualTo("/plugins/ai-foundation/assets/static/brands/xiaomimimo.png")
            .jsonPath("$[0].websiteUrl").isEqualTo("https://mimo.mi.com/")
            .jsonPath("$[0].documentationUrl")
            .isEqualTo("https://mimo.mi.com/docs/en-US/quick-start/summary/welcome")
            .jsonPath("$[0].builtIn").isEqualTo(true)
            .jsonPath("$[0].requiresBaseUrl").isEqualTo(false)
            .jsonPath("$[0].defaultBaseUrl").isEqualTo("https://api.xiaomimimo.com/v1")
            .jsonPath("$[0].completionsPath").isEqualTo("/chat/completions")
            .jsonPath("$[0].supportedAdapterTypes[0]").isEqualTo("mimo-responses")
            .jsonPath("$[0].supportedAdapterTypes[1]").isEqualTo("mimo-chat")
            .jsonPath("$[0].defaultParameterMappings.MAX_OUTPUT_TOKENS.template")
            .isEqualTo("openai.max-completion-tokens")
            .jsonPath("$[0].defaultParameterMappings.REASONING.mode")
            .isEqualTo("TEMPLATE")
            .jsonPath("$[0].defaultParameterMappings.REASONING.template")
            .isEqualTo("reasoning.responses-effort")
            .jsonPath("$[0].parameterDefinitions.length()").isEqualTo(14)
            .jsonPath("$[0].parameterDefinitions[?(@.parameter == 'MAX_OUTPUT_TOKENS')]"
                + ".displayName")
            .isEqualTo("最大输出 Token")
            .jsonPath("$[0].parameterDefinitions[?(@.parameter == 'MAX_OUTPUT_TOKENS')]"
                + ".domain")
            .isEqualTo("language")
            .jsonPath("$[0].parameterDefinitions[?(@.parameter == 'MAX_OUTPUT_TOKENS')]"
                + ".field")
            .isEqualTo("maxOutputTokens")
            .jsonPath("$[0].parameterDefinitions[?(@.parameter == 'MAX_OUTPUT_TOKENS')]"
                + ".common")
            .isEqualTo(true)
            .jsonPath("$[0].parameterDefinitions[?(@.parameter == 'MIN_P')]")
            .isNotEmpty()
            .jsonPath("$[0].parameterMappingTemplates[?(@.id == 'openai.max-tokens')].defaultField")
            .isEqualTo("max_tokens")
            .jsonPath("$[0].parameterMappingTemplates[?(@.id == 'reasoning.thinking-budget')]")
            .isArray()
            .jsonPath("$[0].parameterMappingTemplates[?(@.id == 'reasoning.thinking-type')]"
                + ".defaultReasoningMapping.enabled.field")
            .isEqualTo("thinking.type")
            .jsonPath("$[0].parameterMappingTemplates[?(@.id == 'reasoning.thinking-type')]"
                + ".defaultReasoningMapping.enabled.value")
            .isEqualTo("enabled");
    }

    @Test
    void listProviderTypes_describesProviderOwnedAdaptersAndRecommendations() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("openai", new OpenAiProvider()));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].adapters.length()").isEqualTo(4)
            .jsonPath("$[0].adapters[0].adapterType").isEqualTo("openai-responses")
            .jsonPath("$[0].adapters[0].modelType").isEqualTo("language")
            .jsonPath("$[0].adapters[0].displayName")
            .isEqualTo("Responses API")
            .jsonPath("$[0].adapters[0].description").isNotEmpty()
            .jsonPath("$[0].adapters[0].recommended").isEqualTo(true)
            .jsonPath("$[0].adapters[1].adapterType").isEqualTo("openai-chat")
            .jsonPath("$[0].adapters[1].recommended").isEqualTo(false)
            .jsonPath("$[0].adapters[2].adapterType").isEqualTo("openai-embedding")
            .jsonPath("$[0].adapters[2].recommended").isEqualTo(true)
            .jsonPath("$[0].adapters[3].adapterType").isEqualTo("openai-image")
            .jsonPath("$[0].adapters[3].recommended").isEqualTo(true);
    }

    @Test
    void listProviderTypes_exposesFeaturesForEachDeepSeekAdapter() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("deepseek", new DeepSeekProvider()));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].adapters[0].adapterType").isEqualTo("deepseek-chat")
            .jsonPath("$[0].adapters[0].supportedFeatures")
            .value(features -> org.assertj.core.api.Assertions.assertThat(features.toString())
                .contains("vision", "reasoning"))
            .jsonPath("$[0].adapters[1].adapterType").isEqualTo("deepseek-responses")
            .jsonPath("$[0].adapters[1].supportedFeatures")
            .value(features -> org.assertj.core.api.Assertions.assertThat(features.toString())
                .contains("vision", "reasoning")
                .doesNotContain("audio-input"))
            .jsonPath("$[0].adapters[2].adapterType").isEqualTo("deepseek-messages")
            .jsonPath("$[0].adapters[2].supportedFeatures")
            .value(features -> org.assertj.core.api.Assertions.assertThat(features.toString())
                .contains("vision", "reasoning")
                .doesNotContain("audio-input"));
    }

    @Test
    void listProviderTypes_includesGiteeMoArkMetadata() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("gitee-moark", new GiteeProvider()));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].providerType").isEqualTo("gitee-moark")
            .jsonPath("$[0].displayName").isEqualTo("Gitee 模力方舟")
            .jsonPath("$[0].description").isNotEmpty()
            .jsonPath("$[0].iconUrl")
            .isEqualTo("/plugins/ai-foundation/assets/static/brands/gitee-moark.png")
            .jsonPath("$[0].websiteUrl").isEqualTo("https://ai.gitee.com/")
            .jsonPath("$[0].documentationUrl")
            .isEqualTo("https://ai.gitee.com/docs/products/apis/texts/text-generation")
            .jsonPath("$[0].builtIn").isEqualTo(true)
            .jsonPath("$[0].requiresBaseUrl").isEqualTo(false)
            .jsonPath("$[0].defaultBaseUrl").isEqualTo("https://ai.gitee.com/v1")
            .jsonPath("$[0].completionsPath").isEqualTo("/chat/completions")
            .jsonPath("$[0].supportedAdapterTypes[0]").isEqualTo("gitee-chat");
    }

    @Test
    void listProviderTypes_includesOllamaCompletionsPath() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("ollama", new OllamaProvider()));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].providerType").isEqualTo("ollama")
            .jsonPath("$[0].defaultBaseUrl").isEqualTo("http://localhost:11434")
            .jsonPath("$[0].completionsPath").isEqualTo("/api/chat")
            .jsonPath("$[0].adapters[0].adapterType").isEqualTo("ollama-chat")
            .jsonPath("$[0].adapters[0].displayName").isEqualTo("Ollama Chat API")
            .jsonPath("$[0].adapters[0].description")
            .isEqualTo("使用 Ollama 原生 /api/chat 接口。")
            .jsonPath("$[0].adapters[?(@.adapterType == 'ollama-responses')].displayName")
            .isEqualTo("Responses API");
    }

    @Test
    void listProviderTypes_includesNativeRerankAdapterMetadata() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of(
                "zhipuai", new ZhiPuProvider(),
                "dashscope", new DashScopeProvider(),
                "siliconflow", new SiliconFlowProvider(),
                "ernie", new ErnieProvider(),
                "openrouter", new OpenRouterProvider(),
                "gitee-moark", new GiteeProvider(),
                "aihubmix", new AiHubMixProvider(),
                "doubao", new DouBaoProvider(),
                "openailike", new OpenAiLikeProvider()
            ));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.providerType == 'zhipuai')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("zhipu-chat", "rerank", "zhipu-image"))
            .jsonPath("$[?(@.providerType == 'dashscope')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("rerank"))
            .jsonPath("$[?(@.providerType == 'siliconflow')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("rerank"))
            .jsonPath("$[?(@.providerType == 'ernie')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("rerank", "ernie-image"))
            .jsonPath("$[?(@.providerType == 'openrouter')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("rerank"))
            .jsonPath("$[?(@.providerType == 'gitee-moark')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("rerank", "gitee-image"))
            .jsonPath("$[?(@.providerType == 'aihubmix')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .contains("rerank"))
            .jsonPath("$[?(@.providerType == 'openailike')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'doubao')].supportedAdapterTypes")
            .value(types -> org.assertj.core.api.Assertions.assertThat(types.toString())
                .doesNotContain("rerank"))
            .jsonPath("$[?(@.providerType == 'zhipuai')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'zhipuai')].supportedModelTypes[3]")
            .isEqualTo("image-generation")
            .jsonPath("$[?(@.providerType == 'dashscope')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'siliconflow')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'ernie')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'ernie')].supportedModelTypes[3]")
            .isEqualTo("image-generation")
            .jsonPath("$[?(@.providerType == 'openrouter')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'gitee-moark')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'gitee-moark')].supportedModelTypes[3]")
            .isEqualTo("image-generation")
            .jsonPath("$[?(@.providerType == 'aihubmix')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'openailike')].supportedModelTypes[2]")
            .isEqualTo("rerank");
    }
}
