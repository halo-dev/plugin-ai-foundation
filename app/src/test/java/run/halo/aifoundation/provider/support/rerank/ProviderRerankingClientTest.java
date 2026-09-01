package run.halo.aifoundation.provider.support.rerank;

import run.halo.aifoundation.provider.siliconflow.SiliconFlowRerankingClient;
import run.halo.aifoundation.provider.zhipu.ZhiPuRerankingClient;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.dashscope.DashScopeRerankingClient;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.app.extension.Metadata;

class ProviderRerankingClientTest {

    @Test
    void zhipuClient_mapsRequestAndResponse() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {
                  "id":"rerank-1",
                  "request_id":"req-1",
                  "results":[
                    {"index":1,"relevance_score":0.92,"document":"second"}
                  ],
                  "usage":{"prompt_tokens":12,"total_tokens":16}
                }
                """);
        });

        try {
            var client = new ZhiPuRerankingClient(baseUrl(server), "rerank", "sk-test",
                WebClient.builder());

            StepVerifier.create(client.rerank(request("zhipuai")))
                .assertNext(response -> {
                    assertThat(response.getQuery()).isEqualTo("query");
                    assertThat(response.getResults()).hasSize(1);
                    assertThat(response.getResults().getFirst().getIndex()).isEqualTo(1);
                    assertThat(response.getResults().getFirst().getScore()).isEqualTo(0.92);
                    assertThat(response.getResults().getFirst().getDocument().getText())
                        .isEqualTo("second");
                    assertThat(response.getUsage().getInputTokens()).isEqualTo(12);
                    assertThat(response.getUsage().getTotalTokens()).isEqualTo(16);
                    assertThat(response.getResponse().getId()).isEqualTo("rerank-1");
                    assertThat(response.getResponse().getModel()).isEqualTo("rerank");
                    assertThat(response.getProviderMetadata()).containsEntry("requestId", "req-1");
                })
                .verifyComplete();

            assertThat(capture.get().path()).isEqualTo("/rerank");
            assertThat(capture.get().authorization()).isEqualTo("Bearer sk-test");
            assertThat(capture.get().body())
                .contains("\"model\":\"rerank\"")
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"top_n\":2")
                .doesNotContain("\"return_documents\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dashScopeClient_mapsServiceRequestAndNestedOutputResponse() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {
                  "request_id":"dash-1",
                  "output":{"results":[
                    {"index":0,"relevance_score":0.88,"document":{"text":"first"}}
                  ]},
                  "usage":{"input_tokens":7,"total_tokens":9}
                }
                """);
        });

        try {
            var client = new DashScopeRerankingClient(baseUrl(server), "qwen3-vl-rerank",
                "sk-test",
                WebClient.builder(), DashScopeRerankingClient.RequestFormat.NATIVE);

            StepVerifier.create(client.rerank(request("dashscope")))
                .assertNext(response -> {
                    assertThat(response.getResults()).singleElement()
                        .satisfies(result -> {
                            assertThat(result.getIndex()).isEqualTo(0);
                            assertThat(result.getScore()).isEqualTo(0.88);
                            assertThat(result.getDocument().getText()).isEqualTo("first");
                        });
                    assertThat(response.getUsage().getInputTokens()).isEqualTo(7);
                    assertThat(response.getUsage().getTotalTokens()).isEqualTo(9);
                    assertThat(response.getProviderMetadata().get("endpoint").toString())
                        .endsWith("/api/v1/services/rerank/text-rerank/text-rerank");
                })
                .verifyComplete();

            assertThat(capture.get().path())
                .isEqualTo("/api/v1/services/rerank/text-rerank/text-rerank");
            assertThat(capture.get().body())
                .contains("\"model\":\"qwen3-vl-rerank\"")
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"parameters\":{\"top_n\":2,\"return_documents\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dashScopeClient_usesCompatibleProtocolWithoutInspectingModelId() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {
                  "id":"dash-text-1",
                  "model":"configured-model",
                  "results":[
                    {"index":1,"relevance_score":0.93,"document":{"text":"second"}}
                  ],
                  "usage":{"total_tokens":6}
                }
                """);
        });

        try {
            var client = new DashScopeRerankingClient(baseUrl(server), "configured-model",
                "sk-test", WebClient.builder(),
                DashScopeRerankingClient.RequestFormat.COMPATIBLE);

            StepVerifier.create(client.rerank(request("dashscope")))
                .assertNext(response -> {
                    assertThat(response.getResults()).singleElement()
                        .satisfies(result -> assertThat(result.getIndex()).isEqualTo(1));
                    assertThat(response.getProviderMetadata().get("endpoint").toString())
                        .endsWith("/compatible-api/v1/reranks");
                })
                .verifyComplete();

            assertThat(capture.get().path())
                .isEqualTo("/compatible-api/v1/reranks");
            assertThat(capture.get().body())
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"top_n\":2")
                .doesNotContain("\"input\"")
                .doesNotContain("\"parameters\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dashScopeClient_appliesDocumentedNativeOptions() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {
                  "request_id":"dash-native-options",
                  "output":{"results":[]},
                  "usage":{"input_tokens":1,"total_tokens":1}
                }
                """);
        });

        try {
            var client = new DashScopeRerankingClient(baseUrl(server), "configured-model",
                "sk-test", WebClient.builder(),
                DashScopeRerankingClient.RequestFormat.NATIVE);

            StepVerifier.create(client.rerank(request("dashscope"), null, Map.of(
                    "return_documents", false,
                    "instruct", "Rank for Halo documentation relevance",
                    "fps", 1.0)))
                .expectNextCount(1)
                .verifyComplete();

            assertThat(capture.get().body())
                .contains("\"return_documents\":false")
                .contains("\"fps\":1.0")
                .contains("\"instruct\":\"Rank for Halo documentation relevance\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void siliconFlowClient_mapsTokenUsage() throws Exception {
        var server = server(exchange -> respond(exchange, 200, """
            {
              "id":"sf-1",
              "results":[{"index":0,"relevance_score":"0.77","document":"first"}],
              "tokens":{"input_tokens":5,"output_tokens":2}
            }
            """));

        try {
            var client = new SiliconFlowRerankingClient(baseUrl(server), "sf-rerank", "sk-test",
                WebClient.builder());

            StepVerifier.create(client.rerank(request("siliconflow")))
                .assertNext(response -> {
                    assertThat(response.getResults().getFirst().getScore()).isEqualTo(0.77);
                    assertThat(response.getUsage().getInputTokens()).isEqualTo(5);
                    assertThat(response.getUsage().getTotalTokens()).isEqualTo(7);
                    assertThat(response.getResponse().getMetadata()).containsKey("tokens");
                    assertThat(response.getProviderMetadata()).containsKey("tokens");
                })
                .verifyComplete();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void standardClient_mapsRequestAndCustomHeaders() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {
                  "id":"standard-1",
                  "model":"rerank-standard",
                  "results":[{"index":0,"relevance_score":0.81,"document":"first"}]
                }
                """);
        });

        try {
            var client = new StandardRerankingClient("openrouter", baseUrl(server), "/rerank",
                "rerank-standard", "sk-test", WebClient.builder(),
                Map.of("X-Test-Header", "enabled"));

            StepVerifier.create(client.rerank(request("openrouter")))
                .assertNext(response -> {
                    assertThat(response.getResponse().getId()).isEqualTo("standard-1");
                    assertThat(response.getResponse().getModel()).isEqualTo("rerank-standard");
                    assertThat(response.getResults().getFirst().getScore()).isEqualTo(0.81);
                    assertThat(response.getProviderMetadata()).containsEntry("providerType",
                        "openrouter");
                })
                .verifyComplete();

            assertThat(capture.get().path()).isEqualTo("/rerank");
            assertThat(capture.get().authorization()).isEqualTo("Bearer sk-test");
            assertThat(capture.get().header("X-Test-Header")).isEqualTo("enabled");
            assertThat(capture.get().body())
                .contains("\"model\":\"rerank-standard\"")
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"top_n\":2")
                .contains("\"return_documents\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void openAiLikeProvider_usesConfiguredRerankEndpoint() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {"id":"openailike-1","results":[{"index":0,"relevance_score":0.81}]}
                """);
        });

        try {
            var providerType = new OpenAiLikeProvider();
            var provider = provider(baseUrl(server), "compatible/rerank");
            var client = providerType.buildRerankingClient(provider, "sk-test", "rerank-model");

            StepVerifier.create(client.rerank(request("openailike")))
                .assertNext(response -> assertThat(response.getResponse().getId())
                    .isEqualTo("openailike-1"))
                .verifyComplete();

            assertThat(providerType.getSupportedAdapterTypes())
                .contains(run.halo.aifoundation.provider.support.AdapterType.RERANK);
            assertThat(capture.get().path()).isEqualTo("/compatible/rerank");
            assertThat(capture.get().authorization()).isEqualTo("Bearer sk-test");
            assertThat(capture.get().body())
                .contains("\"model\":\"rerank-model\"")
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"top_n\":2")
                .contains("\"return_documents\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientReportsProviderHttpErrors() throws Exception {
        var server = server(exchange -> respond(exchange, 429, "{\"error\":\"too many\"}"));

        try {
            var client = new ZhiPuRerankingClient(baseUrl(server), "rerank", "sk-test",
                WebClient.builder());

            StepVerifier.create(client.rerank(request("zhipuai")))
                .expectErrorSatisfies(error -> assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("zhipuai rerank request failed")
                    .hasMessageContaining("status=429")
                    .hasMessageContaining("too many"))
                .verify();
        } finally {
            server.stop(0);
        }
    }

    private RerankRequest request(String providerType) {
        return RerankRequest.builder()
            .query("query")
            .documents(List.of(
                RerankDocument.builder().id("first").text("first").build(),
                RerankDocument.builder().id("second").text("second").build()
            ))
            .topN(2)
            .build();
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", handler::handle);
        server.start();
        return server;
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private AiProvider provider(String baseUrl, String rerankEndpointPath) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("openailike");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openailike");
        spec.setBaseUrl(baseUrl);
        spec.setRerankEndpointPath(rerankEndpointPath);
        provider.setSpec(spec);
        return provider;
    }

    private RequestCapture capture(HttpExchange exchange) throws IOException {
        return new RequestCapture(
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            Map.copyOf(exchange.getRequestHeaders()),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        );
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private record RequestCapture(String path, String authorization, Map<String, List<String>> headers,
                                  String body) {
        String header(String name) {
            return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .map(List::getFirst)
                .findFirst()
                .orElse(null);
        }
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
