package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.decision.DecisionModel;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.StackWalkingCallerPluginResolver;
import run.halo.app.extension.ReactiveExtensionClient;

class DecisionConsoleEndpointTest {
    private final AiModelService service = mock(AiModelService.class);
    private final WebTestClient client = WebTestClient.bindToRouterFunction(
        new DecisionConsoleEndpoint(service).endpoint()).build();

    @Test
    void invalidQuestionsAreRejectedBeforeResolvingModel() {
        client.post().uri("/models/configured-model/test-decision").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"state\":\"text\",\"questions\":{}}")
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }

    @Test
    void resolvesResourceNameAndSuppliesLocalAuditMetadata() {
        var model = mock(DecisionModel.class);
        when(service.decisionModel("configured-model")).thenReturn(Mono.just(model));
        when(model.decide(any())).thenReturn(Mono.just(new DecisionResponse("jev-version", Map.of(), null)));
        client.post().uri("/models/configured-model/test-decision").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("state", "{\"comment\":\"hello\"}", "stateFormat", "JSON",
                "questions", Map.of("q", Map.of("type", "YES_NO", "instructions", "Spam?"))))
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.model").isEqualTo("jev-version");
        var request = ArgumentCaptor.forClass(DecisionRequest.class);
        verify(model).decide(request.capture());
        assertThat(request.getValue().getState()).isEqualTo(Map.of("comment", "hello"));
        assertThat(request.getValue().getMetadata()).containsEntry("source", "console-test");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detectsFoundationCallerWhenInvokingDecisionModel(boolean asyncResolution) {
        var resolver = foundationCallerResolver();
        var caller = new AtomicReference<CallerPluginInfo>();
        var model = mock(DecisionModel.class);
        var resolution = Mono.just(model);
        when(service.decisionModel("configured-model")).thenReturn(asyncResolution
            ? resolution.publishOn(reactor.core.scheduler.Schedulers.boundedElastic()) : resolution);
        when(model.decide(any())).thenAnswer(invocation -> {
            caller.set(resolver.resolveCurrentCallerSnapshot());
            return Mono.just(new DecisionResponse("jev-version", Map.of(), null));
        });

        client.post().uri("/models/configured-model/test-decision")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("state", "Hello",
                "questions", Map.of("q", Map.of("type", "YES_NO", "instructions", "Spam?"))))
            .exchange().expectStatus().isOk();

        assertThat(caller.get()).isNotNull();
        assertThat(caller.get().isDetected()).isTrue();
        assertThat(caller.get().getPluginName()).isEqualTo("ai-foundation");
        assertThat(caller.get().getVersion()).isEqualTo("1.0.0");
        assertThat(caller.get().getDetectionSource()).isEqualTo("stack-classloader");
    }

    @SuppressWarnings("unchecked")
    private StackWalkingCallerPluginResolver foundationCallerResolver() {
        var self = mock(PluginWrapper.class);
        var manager = mock(PluginManager.class);
        var descriptor = mock(PluginDescriptor.class);
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(self);
        when(self.getPluginId()).thenReturn("ai-foundation");
        when(self.getPluginManager()).thenReturn(manager);
        when(self.getPluginClassLoader()).thenReturn(DecisionConsoleEndpoint.class.getClassLoader());
        when(self.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getVersion()).thenReturn("1.0.0");
        when(manager.getStartedPlugins()).thenReturn(List.of(self));
        return new StackWalkingCallerPluginResolver(mock(ReactiveExtensionClient.class), provider);
    }
}
