package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

/**
 * Exercises the credential-bearing DeepSeek transport against local HTTP servers.
 *
 * <p>Fixtures cover the documented JSON contracts, fixed API paths, bounded failures and the redirect boundary without
 * requiring a DeepSeek account or consuming billable requests.
 */
class DeepSeekWebClientTest {
    private static final String API_KEY = "local-deepseek-secret";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SEARCH_RESPONSE = """
            {"results":[{"rerank_score":2.4,"title":"Halo CMS","url":"https://www.halo.run/",
            "snippet":"A publishing platform","summary":"Halo documentation and features",
            "content":"","site_name":null,"site_icon":null,"date":null,"authority":3}]}
            """;
    private final List<DisposableServer> servers = new ArrayList<>();

    @AfterEach
    void closeServers() {
        servers.forEach(DisposableServer::disposeNow);
    }

    @Test
    void postsSearchToTheFixedApiPathAndPreservesNullableResultMetadata() {
        var requests = new CopyOnWriteArrayList<CapturedRequest>();
        var server = capture(SEARCH_RESPONSE, requests);
        var body = new LinkedHashMap<String, Object>();
        body.put("query", "Halo site:halo.run lang:zh");
        body.put("count", 5);
        body.put("ori_question", null);
        body.put("need_content", false);
        StepVerifier.create(client(server).search(options(), body))
                .assertNext(response -> {
                    assertThat(response.get("results")).isInstanceOf(List.class);
                    var item = (Map<?, ?>) ((List<?>) response.get("results")).getFirst();
                    assertThat(item.get("title")).isEqualTo("Halo CMS");
                    assertThat(item.containsKey("site_name")).isTrue();
                    assertThat(item.get("site_name")).isNull();
                    assertThat(item.get("site_icon")).isNull();
                    assertThat(item.get("date")).isNull();
                    assertThat(item.get("rerank_score")).isEqualTo(2.4);
                    assertThat(item.get("authority")).isEqualTo(3);
                    assertThat(response.toString()).doesNotContain(API_KEY);
                })
                .verifyComplete();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo("/v1/tools/search");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.contentType()).startsWith("application/json");
            assertThat(readBody(request.body())).isEqualTo(body);
        });
    }

    @Test
    void sendsTheFetchUrlOnlyInTheApiRequestBodyAndAcceptsAnEmptyTitle() {
        var requests = new CopyOnWriteArrayList<CapturedRequest>();
        var server = capture("{\"title\":\"\",\"content\":\"# Halo\\n\\nMarkdown body\"}", requests);
        var target = base(server) + "/a-page-that-must-not-receive-the-api-key?secret=example";
        StepVerifier.create(client(server).fetch(options(), Map.of("url", target)))
                .assertNext(response -> {
                    assertThat(response.get("title")).isEqualTo("");
                    assertThat(response.get("content")).isEqualTo("# Halo\n\nMarkdown body");
                })
                .verifyComplete();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo("/v1/tools/fetch");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(readBody(request.body())).isEqualTo(Map.of("url", target));
        });
    }

    @Test
    void returnsAnEmptySearchAsASuccessfulEmptyResultSet() {
        var server = capture("{\"results\":[]}", new CopyOnWriteArrayList<>());
        StepVerifier.create(client(server).search(options(), Map.of("query", "unmatched")))
                .expectNext(Map.of("results", List.of()))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    void requiresTheToolApiKeyBeforeSendingAnyRequest(String apiKey) {
        var requests = new CopyOnWriteArrayList<CapturedRequest>();
        var server = capture(SEARCH_RESPONSE, requests);
        var options = new DeepSeekWebToolSettings.Options(apiKey, 65_536, 100_000, Duration.ofSeconds(5), 10);
        StepVerifier.create(client(server).search(options, Map.of("query", "Halo")))
                .expectErrorMessage("Configure the DeepSeek API Key in this tool's configuration before using it")
                .verify();
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "400,request rejected",
        "401,API Key is missing or invalid",
        "402,account balance is insufficient",
        "403,security policy",
        "404,no readable webpage content",
        "408,upstream request timed out",
        "413,size limit",
        "429,rate limit exceeded",
        "500,service is unavailable",
        "504,upstream request timed out"
    })
    void exposesOnlySafeStatusDiagnosticsAndDoesNotRetryBillableRequests(int status, String diagnostic) {
        var calls = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    calls.incrementAndGet();
                    return response.status(status)
                            .header("Content-Type", "application/json")
                            .sendString(Mono.just("{\"error\":{\"message\":\"" + API_KEY + " upstream-debug\"}}"));
                })
                .bindNow();
        servers.add(server);
        StepVerifier.create(client(server).search(options(), Map.of("query", "Halo")))
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage())
                            .contains("HTTP " + status, diagnostic)
                            .doesNotContain(API_KEY, "upstream-debug");
                    assertThat(error.getCause()).isNull();
                })
                .verify();
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void rejectsRedirectsWithoutForwardingCredentialsToTheirDestination(int status) {
        var destinationCalls = new AtomicInteger();
        var destination = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    destinationCalls.incrementAndGet();
                    return response.header("Content-Type", "application/json").sendString(Mono.just(SEARCH_RESPONSE));
                })
                .bindNow();
        servers.add(destination);
        var calls = new AtomicInteger();
        var source = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    calls.incrementAndGet();
                    assertThat(request.requestHeaders().get("Authorization")).isEqualTo("Bearer " + API_KEY);
                    return response.status(status)
                            .header("Location", base(destination) + "/capture")
                            .send();
                })
                .bindNow();
        servers.add(source);
        StepVerifier.create(client(source).search(options(), Map.of("query", "Halo")))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("redirects are not allowed"))
                .verify();
        assertThat(calls).hasValue(1);
        assertThat(destinationCalls).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-json",
                "[]",
                "null",
                "{} {}",
                "{\"results\":null}",
                "{\"results\":{}}",
                "{\"results\":[null]}"
            })
    void rejectsMalformedSearchResponsesWithoutIncludingTheirContents(String response) {
        var server = capture(response, new CopyOnWriteArrayList<>());
        StepVerifier.create(client(server).search(options(), Map.of("query", "Halo")))
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage()).startsWith("DeepSeek");
                    assertThat(error.getCause()).isNull();
                })
                .verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "url", "snippet", "summary", "content", "rerank_score", "authority"})
    void rejectsMissingRequiredSearchResultFields(String missingField) throws Exception {
        var response = JSON.readValue(SEARCH_RESPONSE, new TypeReference<Map<String, Object>>() {});
        var result = (Map<?, ?>) ((List<?>) response.get("results")).getFirst();
        result.remove(missingField);
        var server = capture(JSON.writeValueAsString(response), new CopyOnWriteArrayList<>());
        StepVerifier.create(client(server).search(options(), Map.of("query", "Halo")))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("invalid " + missingField))
                .verify();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"title\":null,\"content\":\"body\"}",
                "{\"title\":\"Halo\",\"content\":null}",
                "{\"title\":\"Halo\",\"content\":\"\"}",
                "{\"title\":\"Halo\",\"content\":\" \\n\\t\"}"
            })
    void rejectsFetchResponsesWithoutAStringTitleOrReadableContent(String response) {
        var server = capture(response, new CopyOnWriteArrayList<>());
        StepVerifier.create(client(server).fetch(options(), Map.of("url", "https://www.halo.run/")))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).startsWith("DeepSeek Fetch"))
                .verify();
    }

    @Test
    void boundsTheCompleteResponseBodyBeforeJsonParsing() {
        var response = "{\"title\":\"Halo\",\"content\":\"" + "x".repeat(4_096) + "\"}";
        var server = capture(response, new CopyOnWriteArrayList<>());
        var options = options(256, Duration.ofSeconds(5));
        StepVerifier.create(client(server).fetch(options, Map.of("url", "https://www.halo.run/")))
                .expectErrorMessage("DeepSeek response exceeds the configured response size limit")
                .verify();
    }

    @Test
    void boundsTheWholeRequestIncludingAResponseThatNeverArrives() {
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> Mono.never())
                .bindNow();
        servers.add(server);
        var options = options(65_536, Duration.ofMillis(100));
        StepVerifier.create(client(server).search(options, Map.of("query", "Halo")))
                .expectErrorMessage("DeepSeek request timed out; try again later or increase the tool timeout")
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void doesNotRetryAfterTheServiceReceivesTheBodyButClosesWithoutAResponse() {
        var calls = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .flatMap(body -> {
                            calls.incrementAndGet();
                            response.withConnection(connection -> connection.dispose());
                            return Mono.empty();
                        }))
                .bindNow();
        servers.add(server);
        StepVerifier.create(client(server).search(options(), Map.of("query", "Halo")))
                .expectErrorMessage("Could not connect to the DeepSeek API; check the network and try again later")
                .verify(Duration.ofSeconds(5));
        assertThat(calls).hasValue(1);
    }

    @Test
    void reportsAnEmptyResponseInsteadOfCompletingWithoutAToolResult() {
        var server = capture("", new CopyOnWriteArrayList<>());
        StepVerifier.create(client(server).search(options(), Map.of("query", "Halo")))
                .expectErrorMessage("DeepSeek returned an empty JSON response")
                .verify();
    }

    @Test
    void stripsPotentiallySensitiveConnectionDiagnostics() {
        var server = capture("{}", new CopyOnWriteArrayList<>());
        var unavailableClient = client(server);
        server.disposeNow();
        StepVerifier.create(unavailableClient.search(options(), Map.of("query", "Halo")))
                .expectErrorSatisfies(error -> {
                    assertThat(error.getMessage())
                            .isEqualTo("Could not connect to the DeepSeek API; check the network and try again later");
                    assertThat(error.getCause()).isNull();
                })
                .verify(Duration.ofSeconds(5));
    }

    private DisposableServer capture(String json, List<CapturedRequest> captured) {
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .flatMap(body -> {
                            captured.add(new CapturedRequest(
                                    request.method().name(),
                                    request.uri(),
                                    request.requestHeaders().get("Authorization"),
                                    request.requestHeaders().get("Content-Type"),
                                    body));
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just(json))
                                    .then();
                        }))
                .bindNow();
        servers.add(server);
        return server;
    }

    private static Map<String, Object> readBody(String body) {
        try {
            return JSON.readValue(body, new TypeReference<>() {});
        } catch (Exception error) {
            throw new AssertionError("The API request body must contain JSON", error);
        }
    }

    private static String base(DisposableServer server) {
        return "http://127.0.0.1:" + server.port();
    }

    private static DeepSeekWebClient client(DisposableServer server) {
        return new DeepSeekWebClient(WebClient.builder()
                .baseUrl(base(server))
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().followRedirect(false).disableRetry(true)))
                .build());
    }

    private static DeepSeekWebToolSettings.Options options() {
        return options(65_536, Duration.ofSeconds(5));
    }

    private static DeepSeekWebToolSettings.Options options(int maxBytes, Duration timeout) {
        return new DeepSeekWebToolSettings.Options(API_KEY, maxBytes, 100_000, timeout, 10);
    }

    private record CapturedRequest(String method, String uri, String authorization, String contentType, String body) {}
}
