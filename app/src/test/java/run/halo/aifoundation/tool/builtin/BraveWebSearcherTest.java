package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class BraveWebSearcherTest {
    @Test
    void searchesFixedBraveEndpointWithEncodedQueryAndBoundedResultCount() {
        var http = mock(PublicHttpClient.class);
        var body = """
            {"web":{"results":[{"title":"<b>Halo</b>","url":"https://halo.run/",
            "description":"Open &amp; published"},{"title":"Other","url":"https://example.com/"}]}}
            """.getBytes(StandardCharsets.UTF_8);
        when(http.get(any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(Mono.just(new PublicHttpClient.Response(body, MediaType.APPLICATION_JSON)));
        StepVerifier.create(searcher(http).search(Map.of("query", "Halo & tools", "limit", 1), "test-api-key", false))
                .assertNext(output -> {
                    var results = (List<?>) ((Map<?, ?>) output).get("results");
                    assertThat(results).hasSize(1);
                    assertThat(((Map<?, ?>) results.getFirst()).get("title")).isEqualTo("Halo");
                    assertThat(((Map<?, ?>) results.getFirst()).get("description"))
                            .isEqualTo("Open & published");
                    assertThat(output.toString()).doesNotContain("test-api-key");
                })
                .verifyComplete();
        var uri = ArgumentCaptor.forClass(URI.class);
        verify(http)
                .get(
                        uri.capture(),
                        eq(Map.of("Accept", "application/json", "X-Subscription-Token", "test-api-key")),
                        eq(262_144),
                        eq(List.of(MediaType.APPLICATION_JSON)),
                        eq(false));
        assertThat(uri.getValue().getHost()).isEqualTo("api.search.brave.com");
        assertThat(uri.getValue().getScheme()).isEqualTo("https");
        assertThat(uri.getValue().getPath()).isEqualTo("/res/v1/web/search");
        assertThat(uri.getValue().getUserInfo()).isNull();
        assertThat(uri.getValue().getRawQuery()).contains("q=Halo%20%26%20tools", "count=1");
        // Credential-bearing searches keep the strict, non-redirecting transport.
        verify(http, never()).getText(any(), anyInt(), anyBoolean());
        verify(http, never()).getResource(any(), anyInt(), anyBoolean());
    }

    @Test
    void failsWhenNoCredentialConfiguredInsteadOfFabricatingSearchResults() {
        var http = mock(PublicHttpClient.class);
        StepVerifier.create(searcher(http).search(Map.of("query", "Halo"), "", true))
                .expectErrorMessage(
                        "Configure the Brave Search API Key in this tool's configuration " + "before using web search")
                .verify();
        verifyNoInteractions(http);
    }

    @Test
    void rejectsFractionalAndOutOfRangeLimitsAtExecutorBoundary() {
        var searcher = searcher(mock(PublicHttpClient.class));
        for (var limit : List.of(0, 11, 1.5, Double.NaN)) {
            StepVerifier.create(searcher.search(Map.of("query", "Halo", "limit", limit), "key", false))
                    .expectErrorMessage("limit must be an integer between 1 and 10")
                    .verify();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void forwardsOnlyTheExplicitVpnFlagWithoutChangingTheFixedEndpoint(boolean enabled) {
        var http = mock(PublicHttpClient.class);
        when(http.get(any(), any(), anyInt(), any(), eq(enabled)))
                .thenReturn(Mono.just(new PublicHttpClient.Response(
                        "{}".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON)));
        StepVerifier.create(searcher(http)
                        .search(Map.of("query", "https://attacker.example/?a=b"), "test-only-token", enabled))
                .expectNextCount(1)
                .verifyComplete();
        var uri = ArgumentCaptor.forClass(URI.class);
        verify(http)
                .get(
                        uri.capture(),
                        eq(Map.of("Accept", "application/json", "X-Subscription-Token", "test-only-token")),
                        eq(262_144),
                        eq(List.of(MediaType.APPLICATION_JSON)),
                        eq(enabled));
        assertThat(uri.getValue().getHost()).isEqualTo("api.search.brave.com");
        assertThat(uri.getValue().getPath()).isEqualTo("/res/v1/web/search");
    }

    @Test
    void fakeIpSearchesFailBeforeTransportUnlessExplicitlyEnabledAndStillRejectPrivateDns() throws Exception {
        var fake = InetAddress.getByName("198.18.0.10");
        var exchanges = new AtomicInteger();
        var http = new PublicHttpClient(host -> new InetAddress[] {fake}) {
            @Override
            Mono<Response> exchange(
                    URI uri,
                    Map<String, String> headers,
                    int maxBytes,
                    List<MediaType> types,
                    List<InetAddress> addresses) {
                exchanges.incrementAndGet();
                assertThat(uri.getHost()).isEqualTo("api.search.brave.com");
                assertThat(addresses).containsExactly(fake);
                assertThat(headers).containsOnlyKeys("Accept", "X-Subscription-Token");
                return Mono.just(new Response("{}".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON));
            }
        };
        var searcher = searcher(http);
        StepVerifier.create(searcher.search(Map.of("query", "Halo"), "test-only-token", false))
                .expectErrorMatches(error -> error.getMessage().contains("VPN Fake-IP"))
                .verify();
        assertThat(exchanges).hasValue(0);
        StepVerifier.create(searcher.search(Map.of("query", "Halo"), "test-only-token", true))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(exchanges).hasValue(1);
        var privateHttp = new PublicHttpClient(host -> new InetAddress[] {fake, InetAddress.getLoopbackAddress()});
        StepVerifier.create(searcher(privateHttp).search(Map.of("query", "Halo"), "test-only-token", true))
                .expectErrorMessage("The URL must resolve only to public IP addresses")
                .verify();
    }

    @Test
    void realCredentialBearingTransportRejectsRedirectsBeforeContactingTheDestination() throws Exception {
        var sourceRequests = new AtomicInteger();
        var destinationRequests = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/search", (request, response) -> {
                            sourceRequests.incrementAndGet();
                            assertThat(request.requestHeaders().get("X-Subscription-Token"))
                                    .isEqualTo("local-test-token");
                            return response.status(302)
                                    .header("Location", "/capture-credentials")
                                    .send();
                        })
                        .get("/capture-credentials", (request, response) -> {
                            destinationRequests.incrementAndGet();
                            return response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{}"));
                        }))
                .bindNow();
        try {
            var publicIp = InetAddress.getByName("1.1.1.1");
            var loopback = InetAddress.getByName("127.0.0.1");
            var http = new PublicHttpClient(host -> new InetAddress[] {publicIp}) {
                @Override
                Mono<Response> exchange(
                        URI uri,
                        Map<String, String> headers,
                        int maxBytes,
                        List<MediaType> types,
                        List<InetAddress> addresses) {
                    assertThat(uri.getHost()).isEqualTo("api.search.brave.com");
                    // Exercise the real strict transport with an in-process response fixture.
                    return super.exchange(
                            URI.create("http://public.example:" + server.port() + "/search"),
                            headers,
                            maxBytes,
                            types,
                            List.of(loopback));
                }
            };
            StepVerifier.create(searcher(http).search(Map.of("query", "Halo"), "local-test-token", true))
                    .expectErrorMessage("Redirects are not allowed; use the destination URL directly")
                    .verify();
            assertThat(sourceRequests).hasValue(1);
            assertThat(destinationRequests).hasValue(0);
        } finally {
            server.disposeNow();
        }
    }

    private static BraveWebSearcher searcher(PublicHttpClient http) {
        return new BraveWebSearcher(http, mock(WebSearchSettings.class));
    }
}
