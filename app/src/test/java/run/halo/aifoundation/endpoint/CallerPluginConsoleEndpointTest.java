package run.halo.aifoundation.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginObservationRegistry;

class CallerPluginConsoleEndpointTest {

    private final CallerPluginObservationRegistry observationRegistry =
        mock(CallerPluginObservationRegistry.class);
    private final WebTestClient webTestClient = WebTestClient
        .bindToRouterFunction(new CallerPluginConsoleEndpoint(observationRegistry).endpoint())
        .configureClient()
        .build();

    @Test
    void listObservedCallerPlugins_returnsObservedCallers() {
        when(observationRegistry.list()).thenReturn(List.of(
            CallerPluginInfo.builder()
                .detected(true)
                .detectionSource("stack-classloader")
                .pluginName("halo-agent")
                .displayName("Halo Agent")
                .description("Agent plugin")
                .version("1.0.0")
                .build()
        ));

        webTestClient.get().uri("/observed-caller-plugins")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].detected").isEqualTo(true)
            .jsonPath("$[0].detectionSource").isEqualTo("stack-classloader")
            .jsonPath("$[0].pluginName").isEqualTo("halo-agent")
            .jsonPath("$[0].displayName").isEqualTo("Halo Agent")
            .jsonPath("$[0].description").isEqualTo("Agent plugin")
            .jsonPath("$[0].version").isEqualTo("1.0.0");
    }
}
