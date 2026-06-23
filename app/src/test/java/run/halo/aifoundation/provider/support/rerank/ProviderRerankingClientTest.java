package run.halo.aifoundation.provider.support.rerank;

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
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;

class ProviderRerankingClientTest {

    @Test
    void zhipuClient_mapsRequestAndResponse() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {
                  "request_id":"req-1",
                  "results":[
                    {"index":1,"relevance_score":0.92,"document":"second"}
                  ],
                  "usage":{"prompt_tokens":12,"total_tokens":16}
                }
                """);
        });

        try {
            var client = new ZhiPuRerankingClient(baseUrl(server), "rerank-model", "sk-test",
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
                    assertThat(response.getResponse().getId()).isEqualTo("req-1");
                    assertThat(response.getResponse().getModel()).isEqualTo("rerank-model");
                    assertThat(response.getProviderMetadata()).containsEntry("requestId", "req-1");
                })
                .verifyComplete();

            assertThat(capture.get().path()).isEqualTo("/rerank");
            assertThat(capture.get().authorization()).isEqualTo("Bearer sk-test");
            assertThat(capture.get().body())
                .contains("\"model\":\"rerank-model\"")
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"top_n\":2")
                .contains("\"return_documents\":true")
                .contains("\"return_raw_scores\":true");
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
            var client = new DashScopeRerankingClient(baseUrl(server), "dash-rerank", "sk-test",
                WebClient.builder());

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
                .contains("\"model\":\"dash-rerank\"")
                .contains("\"input\":{\"query\":\"query\",\"documents\":[\"first\",\"second\"]}")
                .contains("\"parameters\":{\"top_n\":2,\"return_documents\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dashScopeClient_canUseCompatibleRerankApi() throws Exception {
        var capture = new AtomicReference<RequestCapture>();
        var server = server(exchange -> {
            capture.set(capture(exchange));
            respond(exchange, 200, """
                {"id":"dash-compatible","results":[{"index":0,"relevance_score":0.8}]}
                """);
        });

        try {
            var client = new DashScopeRerankingClient(baseUrl(server), "dash-rerank", "sk-test",
                WebClient.builder());

            StepVerifier.create(client.rerank(RerankRequest.builder()
                    .query("query")
                    .documents("first", "second")
                    .topN(1)
                    .providerOptions(Map.of("dashscope", Map.of("apiFormat", "compatible")))
                    .build()))
                .assertNext(response -> assertThat(response.getResponse().getId())
                    .isEqualTo("dash-compatible"))
                .verifyComplete();

            assertThat(capture.get().path()).isEqualTo("/compatible-api/v1/reranks");
            assertThat(capture.get().body())
                .contains("\"model\":\"dash-rerank\"")
                .contains("\"query\":\"query\"")
                .contains("\"documents\":[\"first\",\"second\"]")
                .contains("\"top_n\":1")
                .doesNotContain("apiFormat");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void siliconFlowClient_mapsMetaTokenUsage() throws Exception {
        var server = server(exchange -> respond(exchange, 200, """
            {
              "id":"sf-1",
              "results":[{"index":0,"relevance_score":"0.77","document":"first"}],
              "meta":{"tokens":{"input_tokens":5,"output_tokens":2}}
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
                    assertThat(response.getResponse().getMetadata()).containsKey("meta");
                    assertThat(response.getProviderMetadata()).containsKey("rawMeta");
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
                .contains("\"return_documents\":true")
                .contains("\"return_raw_scores\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientReportsProviderHttpErrors() throws Exception {
        var server = server(exchange -> respond(exchange, 429, "{\"error\":\"too many\"}"));

        try {
            var client = new ZhiPuRerankingClient(baseUrl(server), "rerank-model", "sk-test",
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
            .providerOptions(Map.of(providerType, Map.of("return_raw_scores", true)))
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
