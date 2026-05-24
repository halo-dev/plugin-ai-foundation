package run.halo.aifoundation.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.aifoundation.provider.XiaomiMiMoProvider;
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
            .jsonPath("$[0].defaultBaseUrl").isEqualTo("https://api.xiaomimimo.com")
            .jsonPath("$[0].supportedAdapterTypes[0]").isEqualTo("openai-chat");
    }
}
