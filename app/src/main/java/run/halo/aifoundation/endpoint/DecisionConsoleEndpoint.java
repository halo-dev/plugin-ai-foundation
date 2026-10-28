package run.halo.aifoundation.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.service.decision.DecisionValidation;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class DecisionConsoleEndpoint implements CustomEndpoint {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final AiModelService modelService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route().POST("models/{name}/test-decision", this::testDecision,
            builder -> builder.operationId("TestModelDecision")
                .description("Evaluate typed questions against a shared text or JSON state.")
                .tag("console.api.aifoundation.halo.run/v1alpha1/Model")
                .parameter(parameterBuilder().name("name").in(ParameterIn.PATH)
                    .description("Model name (AiModel.metadata.name)")
                    .implementation(String.class).required(true))
                .requestBody(requestBodyBuilder().required(true)
                    .implementation(TestDecisionRequest.class))
                .response(responseBuilder().implementation(DecisionResponse.class)))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> testDecision(ServerRequest request) {
        return request.bodyToMono(TestDecisionRequest.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Decision request is required")))
            .flatMap(body -> {
                var input = DecisionRequest.builder().state(parseState(body))
                    .questions(body.getQuestions()).metadata(Map.of("source", "console-test"))
                    .build();
                return Mono.fromRunnable(() -> DecisionValidation.request(input))
                    .then(Mono.defer(() -> modelService.decisionModel(request.pathVariable("name"))))
                    .flatMap(model -> model.decide(input))
                    .onErrorMap(IllegalArgumentException.class,
                        error -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            error.getMessage(), error));
            })
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    @Data
    public static class TestDecisionRequest {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Text to evaluate, or serialized JSON when stateFormat is JSON")
        private String state;
        @Schema(description = "Input format; defaults to TEXT")
        private StateFormat stateFormat = StateFormat.TEXT;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Independent questions keyed by caller-selected identifiers")
        private Map<String, DecisionQuestion> questions;
    }

    public enum StateFormat {
        TEXT, JSON
    }

    private static Object parseState(TestDecisionRequest request) {
        if (request.getStateFormat() != StateFormat.JSON) {
            return request.getState();
        }
        try {
            return JSON_MAPPER.readValue(request.getState(), Object.class);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "State must be valid JSON", error);
        }
    }
}
