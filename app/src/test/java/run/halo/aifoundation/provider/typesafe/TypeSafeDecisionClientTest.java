package run.halo.aifoundation.provider.typesafe;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.aifoundation.service.decision.DecisionModelImpl;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import tools.jackson.databind.json.JsonMapper;

class TypeSafeDecisionClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> response = new AtomicReference<>("{}");
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void evaluatesMixedQuestionsAndPreservesProbabilitiesAndFractionalScore() {
        response.set("""
            {"model":"jev-1.14.0","answers":{
              "spam":{"type":"noul","noul":0.12},
              "route":{"type":"choice","choice":"review",
                "probabilities":{"review":0.8,"publish":0.2},"confidence":0.6},
              "severity":{"type":"score","score":0.25,"legend":{"0":"low","1":"high"},
                "probabilities":{"0":0.75,"1":0.25},"confidence":0.5}},
              "usage":{"input_tokens":123,"output_tokens":22}}
            """);
        var request = DecisionRequest.builder().state(Map.of("comment", "please review"))
            .questions(Map.of("spam", DecisionQuestion.yesNo("Is this spam?"),
                "route", DecisionQuestion.choice("What next?", Map.of("review", "Review", "publish", "Publish")),
                "severity", DecisionQuestion.score("How severe?", List.of("low", "high"))))
            .metadata(Map.of("private", "local-only")).build();
        var client = client();
        var runtime = new DecisionModelImpl(client, new UsageExecutionObserver(),
            new TypeSafeProvider().decisionCapabilities());
        StepVerifier.create(runtime.decide(request)).assertNext(result -> {
            assertThat(result.getModel()).isEqualTo("jev-1.14.0");
            assertThat(result.getAnswers().get("spam").getProbability()).isEqualTo(0.12);
            assertThat(result.getAnswers().get("spam").getConfidence()).isNull();
            assertThat(result.getAnswers().get("route").getConfidence()).isEqualTo(0.6);
            assertThat(result.getAnswers().get("route").getProbabilities()).containsEntry("review", 0.8);
            assertThat(result.getAnswers().get("severity").getScore()).isEqualTo(0.25);
            assertThat(result.getUsage().getInputTokens()).isEqualTo(123);
            assertThat(result.getUsage().getOutputTokens()).isEqualTo(22);
        }).verifyComplete();
        assertThat(path.get()).isEqualTo("/v1/systemone");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        var body = JsonMapper.builder().build().readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("jev-latest");
        assertThat(body.path("questions").path("spam").path("type").asText()).isEqualTo("noul");
        assertThat(body.path("questions").path("route").path("criteria").path("review").asText()).isEqualTo("Review");
        assertThat(body.path("questions").path("severity").path("criteria").size()).isEqualTo(2);
        assertThat(requestBody.get()).doesNotContain("local-only", "metadata", "messages");
    }

    @Test
    void discoversModelsUsingTypeSafeCatalogShape() {
        response.set("""
            {"models":[{"name":"jev-latest","description":"Decision model","release_date":"2026-09-15"}]}
            """);
        var provider = new AiProvider();
        provider.setSpec(new AiProvider.AiProviderSpec());
        provider.getSpec().setBaseUrl(baseUrl + "/");
        StepVerifier.create(new TypeSafeProvider().discoverModels(provider, "test-key"))
            .assertNext(models -> {
                assertThat(models).hasSize(1);
                assertThat(models.getFirst().modelId()).isEqualTo("jev-latest");
                assertThat(models.getFirst().modelType()).isEqualTo(ModelType.DECISION);
            }).verifyComplete();
        assertThat(path.get()).isEqualTo("/v1/models");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
    }

    @Test
    void preservesMissingUsageAsUnknownAndMapsYesNoCriteria() {
        response.set("""
            {"model":"jev-version","answers":{"q":{"type":"noul","noul":0.5}},"usage":{"input_tokens":null}}
            """);
        var question = DecisionQuestion.yesNo("Urgent?");
        question.setYesCriteria("Time-sensitive");
        question.setNoCriteria("Can wait");
        StepVerifier.create(client().decide(DecisionRequest.builder().state("message")
                .questions(Map.of("q", question)).build()))
            .assertNext(result -> {
                assertThat(result.getUsage().getInputTokens()).isNull();
                assertThat(result.getUsage().getOutputTokens()).isNull();
            }).verifyComplete();
        assertThat(requestBody.get()).contains("\"true\":\"Time-sensitive\"", "\"false\":\"Can wait\"");
    }

    @Test
    void stillRejectsMissingTypeSafeProtocolFields() {
        var question = DecisionQuestion.choice("Route?", Map.of("a", "A", "b", "B"));
        response.set("""
            {"model":"jev-version","answers":{"q":{"type":"choice","choice":"a",
              "probabilities":{"a":0.8,"b":0.2}}}}
            """);
        StepVerifier.create(client().decide(DecisionRequest.builder().state("message")
            .questions(Map.of("q", question)).build()))
            .expectError(IllegalStateException.class).verify();

        response.set("""
            {"model":"jev-version","answers":{"q":{"type":"score","score":0.2,
              "confidence":0.6,"probabilities":{"0":0.8,"1":0.2}}}}
            """);
        StepVerifier.create(client().decide(DecisionRequest.builder().state("message")
            .questions(Map.of("q", DecisionQuestion.score("Score?", List.of("low", "high")))).build()))
            .expectError(IllegalStateException.class).verify();
    }

    @Test
    void propagatesProviderRateLimitErrors() {
        status.set(429);
        response.set("{\"detail\":\"Rate limited\"}");
        StepVerifier.create(client().decide(DecisionRequest.builder().state("message")
                .questions(Map.of("q", DecisionQuestion.yesNo("Urgent?"))).build()))
            .expectError(ProviderHttpException.class).verify();
    }

    private TypeSafeDecisionClient client() {
        return new TypeSafeDecisionClient(baseUrl + "/", "test-key", "jev-latest", WebClient.builder());
    }
}
