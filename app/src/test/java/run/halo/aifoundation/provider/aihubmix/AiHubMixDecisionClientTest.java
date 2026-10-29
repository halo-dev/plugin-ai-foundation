package run.halo.aifoundation.provider.aihubmix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.aifoundation.service.decision.DecisionModelImpl;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import tools.jackson.databind.json.JsonMapper;

@ProviderContractSource(
    provider = "aihubmix",
    officialDocumentation = "https://aihubmix.com/model/jev-latest/llms.txt; "
        + "https://aihubmix.com/api/v1/models; "
        + "https://docs.aihubmix.com/cn/api/App-code",
    retrievedAt = "2026-10-28"
)
class AiHubMixDecisionClientTest {
    private final AiHubMixProvider providerType = new AiHubMixProvider();
    private final AtomicReference<String> response = new AtomicReference<>("{}");
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> appCode = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            appCode.set(exchange.getRequestHeaders().getFirst("APP-Code"));
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
    void discoversAndCallsDecisionModelsFromCatalogTypeWithoutModelNameInference() {
        // Jev fields match the public catalog retrieved on 2026-10-28.
        response.set("""
            {"data":[
              {"model_id":"jev-latest","types":"decision","vendor":"typesafe","endpoints":""},
              {"model_id":"jev-1.13","types":"decision","vendor":"typesafe","endpoints":""},
              {"model_id":"jev-1.14","types":"decision","vendor":"typesafe","endpoints":""},
              {"model_id":"future-judge","type":["decision"]},
              {"model_id":"jev-chat","types":"llm"},
              {"model_id":"voice-model","types":"tts"}],"success":true}
            """);
        var provider = provider("/v1/");
        var models = providerType.discoverModels(provider, "test-key").block();
        assertThat(path.get()).isEqualTo("/api/v1/models");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(appCode.get()).isEqualTo("NEUE3459");
        assertThat(models).hasSize(5);
        assertThat(models.getLast().modelType()).isEqualTo(ModelType.LANGUAGE);
        var decisions = models.stream().filter(model -> model.modelType() == ModelType.DECISION)
            .toList();
        assertThat(decisions).extracting(model -> model.modelId())
            .containsExactly("jev-latest", "jev-1.13", "jev-1.14", "future-judge");
        for (var model : decisions) {
            assertThat(model.adapterType()).isEqualTo(AdapterType.AIHUBMIX_SYSTEM_ONE);
            assertThat(model.source()).isEqualTo(DiscoverySource.REMOTE);
            assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.HIGH);
            response.set("""
                {"model":"served-version","answers":{"urgent":{"type":"noul","noul":0.9}}}
                """);
            var client = providerType.buildDecisionClient(provider, "test-key", new ProviderModelRef(
                model.modelId(), model.modelType(), model.adapterType()));
            StepVerifier.create(client.decide(yesNoRequest())).assertNext(result -> {
                assertThat(result.getAnswers().get("urgent").getProbability()).isEqualTo(0.9);
                assertThat(result.getUsage()).isNull();
            }).verifyComplete();
            assertThat(path.get()).isEqualTo("/v1/systemone");
            assertThat(JsonMapper.builder().build().readTree(requestBody.get()).path("model").asText())
                .isEqualTo(model.modelId());
        }
    }

    @ParameterizedTest
    @CsvSource({"'', /v1/systemone", "/, /v1/systemone", "/v1, /v1/systemone",
        "/v1/, /v1/systemone", "/gateway/v1/, /gateway/v1/systemone"})
    void evaluatesMixedQuestionsWithGatewayHeadersAndNormalizedBaseUrl(String suffix, String endpoint) {
        response.set("""
            {"model":"jev-1.14.0","answers":{
              "urgent":{"type":"noul","noul":0.9},
              "route":{"type":"choice","choice":"review",
                "probabilities":{"review":0.8,"publish":0.2},"confidence":0.6},
              "severity":{"type":"score","score":0.25,"legend":{"0":"low","1":"high"},
                "probabilities":{"0":0.75,"1":0.25},"confidence":0.5}},
              "usage":{"input_tokens":123,"output_tokens":22}}
            """);
        var request = DecisionRequest.builder().state(Map.of("comment", "please review"))
            .questions(Map.of("urgent", DecisionQuestion.yesNo("Is this urgent?"),
                "route", DecisionQuestion.choice("What next?",
                    Map.of("review", "Review", "publish", "Publish")),
                "severity", DecisionQuestion.score("How severe?", List.of("low", "high"))))
            .metadata(Map.of("private", "local-only")).build();
        // A missing adapter resolves to the provider's decision adapter, including manual imports.
        var client = providerType.buildDecisionClient(provider(suffix), "test-key",
            new ProviderModelRef("jev-latest", ModelType.DECISION, null));
        var runtime = new DecisionModelImpl(client, new UsageExecutionObserver(),
            providerType.decisionCapabilities());
        StepVerifier.create(runtime.decide(request)).assertNext(result -> {
            assertThat(result.getModel()).isEqualTo("jev-1.14.0");
            assertThat(result.getAnswers().get("urgent").getProbability()).isEqualTo(0.9);
            assertThat(result.getAnswers().get("route").getChoice()).isEqualTo("review");
            assertThat(result.getAnswers().get("route").getConfidence()).isEqualTo(0.6);
            assertThat(result.getAnswers().get("severity").getScore()).isEqualTo(0.25);
            assertThat(result.getAnswers().get("severity").getLegend()).containsEntry("0", "low");
            assertThat(result.getUsage().getInputTokens()).isEqualTo(123);
            assertThat(result.getUsage().getOutputTokens()).isEqualTo(22);
        }).verifyComplete();
        assertThat(path.get()).isEqualTo(endpoint);
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        assertThat(appCode.get()).isEqualTo("NEUE3459");
        var body = JsonMapper.builder().build().readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("jev-latest");
        assertThat(body.path("state").path("comment").asText()).isEqualTo("please review");
        assertThat(body.path("questions").path("urgent").path("type").asText()).isEqualTo("noul");
        assertThat(body.path("questions").path("route").path("criteria").path("review").asText())
            .isEqualTo("Review");
        assertThat(body.path("questions").path("severity").path("criteria").size()).isEqualTo(2);
        assertThat(requestBody.get()).doesNotContain("local-only", "metadata", "messages");
    }

    @Test
    void attributesGatewayErrorsToAiHubMix() {
        status.set(429);
        response.set("{\"error\":{\"message\":\"Rate limited\"},\"tid\":\"trace-123\"}");
        var client = providerType.buildDecisionClient(provider("/v1"), "test-key",
            new ProviderModelRef("jev-latest", ModelType.DECISION, AdapterType.AIHUBMIX_SYSTEM_ONE));
        StepVerifier.create(client.decide(yesNoRequest())).expectErrorSatisfies(error -> {
            assertThat(error).isInstanceOf(ProviderHttpException.class);
            var http = (ProviderHttpException) error;
            assertThat(http.getProviderType()).isEqualTo("aihubmix");
            assertThat(http.getOperation()).isEqualTo("decision");
            assertThat(http.getStatusCode()).isEqualTo(429);
            assertThat(http.getResponseBody()).contains("trace-123");
        }).verify();
    }

    @Test
    void rejectsUnsupportedDecisionOptionsWithoutChangingOtherDomains() {
        var decision = new ProviderModelRef("jev-latest", ModelType.DECISION,
            AdapterType.AIHUBMIX_SYSTEM_ONE, Map.of("temperature", 0.5));
        assertThatThrownBy(() -> providerType.validateNativeModelOptions(decision))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("native options");
        assertThatThrownBy(() -> providerType.buildDecisionClient(provider("/v1"), "key", decision))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("native options");
        var language = new ProviderModelRef("chat-model", ModelType.LANGUAGE,
            AdapterType.AIHUBMIX_RESPONSES, Map.of("temperature", 0.5));
        providerType.validateNativeModelOptions(language);
        assertThatThrownBy(() -> providerType.buildDecisionClient(provider("/v1"), "key", language))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("decision adapter");
    }

    private AiProvider provider(String suffix) {
        var provider = new AiProvider();
        provider.setSpec(new AiProvider.AiProviderSpec());
        provider.getSpec().setProviderType("aihubmix");
        provider.getSpec().setBaseUrl(baseUrl + suffix);
        return provider;
    }

    private DecisionRequest yesNoRequest() {
        return DecisionRequest.builder().state("Please help now")
            .questions(Map.of("urgent", DecisionQuestion.yesNo("Is this urgent?"))).build();
    }
}
