package run.halo.aifoundation.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.tool.AiToolServiceImpl;
import run.halo.aifoundation.tool.ToolDescriptor;

class ToolConsoleEndpointTest {
    private final AiToolServiceImpl service = mock(AiToolServiceImpl.class);
    private final WebTestClient client = WebTestClient.bindToRouterFunction(
        new ToolConsoleEndpoint(service).endpoint()).build();

    @Test
    void listsMetadataWithoutExecutableFields() {
        when(service.listTools()).thenReturn(Flux.just(new ToolDescriptor("demo/clock", "demo",
            "clock", "clock_123", "时钟", "Current time", "获取当前时间", "utility",
            Map.of("type", "object"), Map.of("type", "string"), List.of(), List.of(), true, true,
            null, null)));

        client.get().uri("/tools").exchange().expectStatus().isOk().expectBody()
            .jsonPath("$[0].id").isEqualTo("demo/clock")
            .jsonPath("$[0].pluginName").isEqualTo("demo")
            .jsonPath("$[0].displayName").isEqualTo("时钟")
            .jsonPath("$[0].inputSchema.type").isEqualTo("object")
            .jsonPath("$[0].outputSchema.type").isEqualTo("string")
            .jsonPath("$[0].executor").doesNotExist()
            .jsonPath("$[0].approvalPolicy").doesNotExist()
            .jsonPath("$[0].onInputStart").doesNotExist();
    }

    @Test
    void exposesNoArbitraryHttpExecutionRoute() {
        client.post().uri("/tools/demo/clock/execute").exchange().expectStatus().isNotFound();
    }

    @Test
    void stateAndConfigurationRoutesUseStableNamespacedId() {
        var descriptor = new ToolDescriptor("demo/clock", "demo", "clock", "clock_123", "时钟",
            "Current time", "获取当前时间", "utility", Map.of("type", "object"), null,
            List.of(), List.of(), true, true, null, null);
        when(service.updateEnabled("demo/clock", false)).thenReturn(Mono.just(descriptor));
        when(service.getConfiguration("demo/clock"))
            .thenReturn(Mono.just(Map.of("secretName", "reference")));
        when(service.updateConfiguration("demo/clock", Map.of("secretName", "changed")))
            .thenReturn(Mono.just(Map.of("secretName", "changed")));

        client.put().uri("/tools/demo/clock/state").bodyValue(Map.of("enabled", false))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.id").isEqualTo("demo/clock");
        client.get().uri("/tools/demo/clock/configuration").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.values.secretName").isEqualTo("reference");
        client.put().uri("/tools/demo/clock/configuration")
            .bodyValue(Map.of("values", Map.of("secretName", "changed")))
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.values.secretName").isEqualTo("changed");
    }

    @Test
    void invalidStateOrConfigurationReturnsBadRequest() {
        client.put().uri("/tools/demo/clock/state").bodyValue(Map.of())
            .exchange().expectStatus().isBadRequest();
        when(service.updateConfiguration("demo/clock", Map.of("rawKey", "not-allowed")))
            .thenReturn(Mono.error(new IllegalArgumentException("Unknown configuration field")));
        client.put().uri("/tools/demo/clock/configuration")
            .bodyValue(Map.of("values", Map.of("rawKey", "not-allowed")))
            .exchange().expectStatus().isBadRequest();
    }
}
