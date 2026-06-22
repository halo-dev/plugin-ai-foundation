package run.halo.aifoundation.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.aifoundation.provider.AiHubMixProvider;
import run.halo.aifoundation.provider.DashScopeProvider;
import run.halo.aifoundation.provider.DouBaoProvider;
import run.halo.aifoundation.provider.ErnieProvider;
import run.halo.aifoundation.provider.GiteeMoArkProvider;
import run.halo.aifoundation.provider.OllamaProvider;
import run.halo.aifoundation.provider.OpenRouterProvider;
import run.halo.aifoundation.provider.SiliconFlowProvider;
import run.halo.aifoundation.provider.XiaomiMiMoProvider;
import run.halo.aifoundation.provider.ZhiPuProvider;
import run.halo.aifoundation.provider.support.ProviderClientCache;

class ProviderTypeConsoleEndpointTest {

    private final ProviderClientCache providerClientCache = mock(ProviderClientCache.class);
    private final WebTestClient webTestClient = WebTestClient
        .bindToRouterFunction(new ProviderTypeConsoleEndpoint(providerClientCache).endpoint())
        .configureClient()
        .build();

    @Test
    void listProviderTypes_includesXiaomiMiMoMetadata() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("mimo", new XiaomiMiMoProvider()));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].providerType").isEqualTo("mimo")
            .jsonPath("$[0].displayName").isEqualTo("Xiaomi MiMo")
            .jsonPath("$[0].description").isNotEmpty()
            .jsonPath("$[0].iconUrl")
            .isEqualTo("/plugins/ai-foundation/assets/static/brands/xiaomimimo.png")
            .jsonPath("$[0].websiteUrl").isEqualTo("https://platform.xiaomimimo.com/")
            .jsonPath("$[0].documentationUrl")
            .isEqualTo("https://platform.xiaomimimo.com/#/docs/welcome")
            .jsonPath("$[0].builtIn").isEqualTo(true)
            .jsonPath("$[0].requiresBaseUrl").isEqualTo(false)
            .jsonPath("$[0].defaultBaseUrl").isEqualTo("https://api.xiaomimimo.com/v1")
            .jsonPath("$[0].completionsPath").isEqualTo("/chat/completions")
            .jsonPath("$[0].supportedAdapterTypes[0]").isEqualTo("openai-chat");
    }

    @Test
    void listProviderTypes_includesGiteeMoArkMetadata() {
        when(providerClientCache.getProviderTypeMap())
            .thenReturn(Map.of("gitee-moark", new GiteeMoArkProvider()));

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
            .jsonPath("$[0].supportedAdapterTypes[0]").isEqualTo("openai-chat");
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
            .jsonPath("$[0].completionsPath").isEqualTo("/api/chat");
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
                "gitee-moark", new GiteeMoArkProvider(),
                "aihubmix", new AiHubMixProvider(),
                "doubao", new DouBaoProvider()
            ));

        webTestClient.get().uri("/provider-types")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[?(@.providerType == 'zhipuai')].supportedAdapterTypes[0]")
            .isEqualTo("openai-chat")
            .jsonPath("$[?(@.providerType == 'zhipuai')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'dashscope')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'siliconflow')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'ernie')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'openrouter')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'gitee-moark')].supportedAdapterTypes[1]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'aihubmix')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'doubao')].supportedAdapterTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'zhipuai')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'dashscope')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'siliconflow')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'ernie')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'openrouter')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'gitee-moark')].supportedModelTypes[1]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'aihubmix')].supportedModelTypes[2]")
            .isEqualTo("rerank")
            .jsonPath("$[?(@.providerType == 'doubao')].supportedModelTypes[2]")
            .isEqualTo("rerank");
    }
}
