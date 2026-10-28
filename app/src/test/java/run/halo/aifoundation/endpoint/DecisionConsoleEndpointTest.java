package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.decision.DecisionModel;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;

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
}
