package run.halo.aifoundation.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginObservationRegistry;
import run.halo.aifoundation.service.usage.UsageCallerPlugin;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CallerPluginConsoleEndpointTest {

    private final UsageStatisticsService usageStatisticsService =
        mock(UsageStatisticsService.class);
    private final CallerPluginObservationRegistry observationRegistry =
        mock(CallerPluginObservationRegistry.class);
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final WebTestClient webTestClient = WebTestClient
        .bindToRouterFunction(new CallerPluginConsoleEndpoint(usageStatisticsService,
            observationRegistry, client).endpoint())
        .configureClient()
        .build();

    @Test
    void listObservedCallerPlugins_returnsRecordedCallersEnrichedWithMetadata() {
        when(usageStatisticsService.callerPlugins()).thenReturn(Mono.just(List.of(
            new UsageCallerPlugin("halo-agent", "1.0.0", "stack-classloader"),
            new UsageCallerPlugin("removed-plugin", "0.9.0", "stack-classloader"))));
        when(client.fetch(Plugin.class, "halo-agent")).thenReturn(Mono.just(plugin()));
        when(client.fetch(Plugin.class, "removed-plugin")).thenReturn(Mono.empty());

        webTestClient.get().uri("/observed-caller-plugins")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].detected").isEqualTo(true)
            .jsonPath("$[0].detectionSource").isEqualTo("stack-classloader")
            .jsonPath("$[0].pluginName").isEqualTo("halo-agent")
            .jsonPath("$[0].displayName").isEqualTo("Halo Agent")
            .jsonPath("$[0].version").isEqualTo("1.0.0")
            .jsonPath("$[1].pluginName").isEqualTo("removed-plugin")
            .jsonPath("$[1].displayName").isEmpty()
            .jsonPath("$[1].version").isEqualTo("0.9.0");
    }

    @Test
    void listObservedCallerPlugins_fallsBackToObservedCallersWhenStatisticsUnavailable() {
        when(usageStatisticsService.callerPlugins())
            .thenReturn(Mono.error(new IllegalStateException("unavailable")));
        when(observationRegistry.list()).thenReturn(List.of(
            CallerPluginInfo.builder()
                .detected(true)
                .detectionSource("stack-classloader")
                .pluginName("halo-agent")
                .displayName("Halo Agent")
                .version("1.0.0")
                .build()));

        webTestClient.get().uri("/observed-caller-plugins")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].pluginName").isEqualTo("halo-agent")
            .jsonPath("$[0].displayName").isEqualTo("Halo Agent");
    }

    private Plugin plugin() {
        var plugin = new Plugin();
        var metadata = new Metadata();
        metadata.setName("halo-agent");
        plugin.setMetadata(metadata);
        var spec = new Plugin.PluginSpec();
        spec.setDisplayName("Halo Agent");
        spec.setVersion("1.0.0");
        plugin.setSpec(spec);
        return plugin;
    }
}
