package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class PublicHttpClientResourceTest {
    @Test
    void binaryRedirectsRevalidateEveryHopAndReturnTheFinalUri() throws Exception {
        var hosts = new CopyOnWriteArrayList<String>();
        var calls = new CopyOnWriteArrayList<URI>();
        var ip = InetAddress.getByName("1.1.1.1");
        var client =
                new PublicHttpClient(host -> {
                    hosts.add(host);
                    return new InetAddress[] {ip};
                }) {
                    @Override
                    Mono<Response> exchangeResource(URI uri, int maxBytes, List<InetAddress> addresses) {
                        calls.add(uri);
                        assertThat(addresses).containsExactly(ip);
                        return uri.getPath().equals("/start")
                                ? Mono.error(new RedirectException(URI.create("https://cdn.example/final.pdf")))
                                : Mono.just(new Response(new byte[] {1, 2, 3}, MediaType.APPLICATION_PDF));
                    }
                };
        StepVerifier.create(client.getResource(URI.create("https://example.com/start"), 128, false))
                .assertNext(value -> assertThat(value.uri()).isEqualTo(URI.create("https://cdn.example/final.pdf")))
                .verifyComplete();
        assertThat(hosts).containsExactly("example.com", "cdn.example");
        assertThat(calls).hasSize(2);
    }

    @Test
    void binaryRedirectsCannotReachPrivateDnsAnswersOrDowngradeHttps() throws Exception {
        var exchanges = new AtomicInteger();
        var client =
                new PublicHttpClient(host -> new InetAddress[] {
                    InetAddress.getByName(host.equals("private.example") ? "127.0.0.1" : "1.1.1.1")
                }) {
                    @Override
                    Mono<Response> exchangeResource(URI uri, int maxBytes, List<InetAddress> addresses) {
                        exchanges.incrementAndGet();
                        var target = uri.getPath().equals("/private")
                                ? "https://private.example/file.pdf"
                                : "http://example.com/file.pdf";
                        return Mono.error(new RedirectException(URI.create(target)));
                    }
                };
        StepVerifier.create(client.getResource(URI.create("https://example.com/private"), 128, false))
                .expectErrorMessage("The URL must resolve only to public IP addresses")
                .verify();
        StepVerifier.create(client.getResource(URI.create("https://example.com/downgrade"), 128, false))
                .expectErrorMessage("HTTPS redirects to HTTP are not allowed")
                .verify();
        assertThat(exchanges).hasValue(2);
    }

    @Test
    void binaryFakeIpCompatibilityIsExplicitAndDoesNotAllowOtherPrivateAnswers() throws Exception {
        var fake = InetAddress.getByName("198.18.0.4");
        var exchanges = new AtomicInteger();
        var client = new PublicHttpClient(host -> new InetAddress[] {fake}) {
            @Override
            Mono<Response> exchangeResource(URI uri, int maxBytes, List<InetAddress> addresses) {
                exchanges.incrementAndGet();
                assertThat(addresses).containsExactly(fake);
                return Mono.just(new Response(new byte[] {1}, MediaType.APPLICATION_PDF));
            }
        };
        var uri = URI.create("https://example.com/file.pdf");
        StepVerifier.create(client.getResource(uri, 128, false))
                .expectErrorMatches(error -> error.getMessage().contains("VPN Fake-IP"))
                .verify();
        StepVerifier.create(client.getResource(uri, 128, true))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(client.getResource(URI.create("http://example.com/file.pdf"), 128, true))
                .expectErrorMatches(error -> error.getMessage().contains("VPN Fake-IP"))
                .verify();
        assertThat(exchanges).hasValue(1);
        var mixed = new PublicHttpClient(host -> new InetAddress[] {fake, InetAddress.getLoopbackAddress()});
        StepVerifier.create(mixed.getResource(uri, 128, true))
                .expectErrorMessage("The URL must resolve only to public IP addresses")
                .verify();
    }

    @Test
    void realBinaryTransportAcceptsPdfAndBoundsChunkedAndDecompressedBytes() throws Exception {
        var bytes = "%PDF-fixture".getBytes(StandardCharsets.UTF_8);
        var compressed = gzip(bytes);
        var compressedLarge = gzip(new byte[16_384]);
        var headers = new AtomicReference<io.netty.handler.codec.http.HttpHeaders>();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/file.pdf", (request, response) -> {
                            headers.set(request.requestHeaders().copy());
                            return response.header("Content-Type", "application/pdf")
                                    .header("Content-Encoding", "gzip")
                                    .sendByteArray(Mono.just(compressed));
                        })
                        .get(
                                "/chunked",
                                (request, response) -> response.header("Content-Type", "application/pdf")
                                        .sendByteArray(Flux.just(new byte[100], new byte[100])))
                        .get(
                                "/compressed-large",
                                (request, response) -> response.header("Content-Type", "application/pdf")
                                        .header("Content-Encoding", "gzip")
                                        .sendByteArray(Mono.just(compressedLarge))))
                .bindNow();
        try {
            var client = new PublicHttpClient();
            var addresses = List.of(InetAddress.getByName("127.0.0.1"));
            var base = "http://public.example:" + server.port();
            StepVerifier.create(client.exchangeResource(URI.create(base + "/file.pdf"), 128, addresses))
                    .assertNext(value -> assertThat(value.body()).isEqualTo(bytes))
                    .verifyComplete();
            assertThat(headers.get().get(HttpHeaders.COOKIE)).isNull();
            assertThat(headers.get().get(HttpHeaders.AUTHORIZATION)).isNull();
            for (var path : List.of("/chunked", "/compressed-large")) {
                StepVerifier.create(client.exchangeResource(URI.create(base + path), 128, addresses))
                        .expectError(org.springframework.core.io.buffer.DataBufferLimitException.class)
                        .verify();
            }
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void localAttachmentTransportUsesTheTrustedListenerAndForwardsNoCallerCredentials() {
        var headers = new AtomicReference<io.netty.handler.codec.http.HttpHeaders>();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/upload/file.pdf", (request, response) -> {
                    headers.set(request.requestHeaders().copy());
                    return response.header("Content-Type", "application/pdf")
                            .sendByteArray(Mono.just(new byte[] {1, 2, 3}));
                }))
                .bindNow();
        try {
            var client = new PublicHttpClient();
            var exchange = caller(server.port());
            for (var location : List.of("/upload/file.pdf", "http://localhost:" + server.port() + "/upload/file.pdf")) {
                StepVerifier.create(withExchange(client.getLocalAttachment(URI.create(location), 128), exchange))
                        .assertNext(value -> assertThat(value.body()).containsExactly((byte) 1, (byte) 2, (byte) 3))
                        .verifyComplete();
            }
            assertThat(headers.get().get(HttpHeaders.HOST)).isEqualTo("127.0.0.1:" + server.port());
            for (var name : List.of(HttpHeaders.COOKIE, HttpHeaders.AUTHORIZATION, "Forwarded", "X-Api-Key")) {
                assertThat(headers.get().get(name)).isNull();
            }
        } finally {
            server.disposeNow();
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/apis/api.console.halo.run/v1alpha1/users",
                "/upload/../private",
                "/upload/%2e%2e/private",
                "/upload/a%5cb",
                "/upload/file.pdf?token=secret",
                "//attacker.example/upload/file",
                "http://localhost:8091/upload/file",
                "http://user:pass@localhost:8090/upload/file",
                "https://example.com/upload/file"
            })
    void localAttachmentTransportRejectsOtherOriginsPathsQueriesAndTraversal(String location) {
        StepVerifier.create(withExchange(
                        new PublicHttpClient().getLocalAttachment(URI.create(location), 128), caller(8090)))
                .expectErrorMessage("Unsupported local attachment URL")
                .verify();
    }

    @Test
    void localAttachmentTransportRequiresTheCurrentRequestAndDoesNotFollowRedirects() {
        var client = new PublicHttpClient();
        StepVerifier.create(client.getLocalAttachment(URI.create("/upload/file.pdf"), 128))
                .expectErrorMessage("Local attachments require the current Halo HTTP request")
                .verify();
        var targets = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get(
                                "/upload/file.pdf",
                                (request, response) -> response.status(302)
                                        .header("Location", "/private")
                                        .send())
                        .get("/private", (request, response) -> {
                            targets.incrementAndGet();
                            return response.sendString(Mono.just("private"));
                        }))
                .bindNow();
        try {
            StepVerifier.create(withExchange(
                            client.getLocalAttachment(URI.create("/upload/file.pdf"), 128), caller(server.port())))
                    .expectErrorMessage("External HTTP request failed")
                    .verify();
            assertThat(targets).hasValue(0);
        } finally {
            server.disposeNow();
        }
    }

    private static MockServerWebExchange caller(int port) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("https://attacker.example/invoke")
                .localAddress(new InetSocketAddress("127.0.0.1", port))
                .header(HttpHeaders.HOST, "attacker.example")
                .header("Forwarded", "host=attacker.example;proto=https")
                .header(HttpHeaders.COOKIE, "SESSION=current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer must-not-forward")
                .header("X-Api-Key", "must-not-forward")
                .build());
    }

    private static <T> Mono<T> withExchange(Mono<T> action, MockServerWebExchange exchange) {
        var result = new AtomicReference<T>();
        return new ServerWebExchangeContextFilter()
                .filter(exchange, ignored -> action.doOnNext(result::set).then())
                .then(Mono.fromSupplier(result::get));
    }

    private static byte[] gzip(byte[] bytes) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }
}
