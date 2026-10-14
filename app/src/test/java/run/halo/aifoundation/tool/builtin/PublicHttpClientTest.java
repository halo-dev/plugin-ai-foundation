package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.util.concurrent.DefaultEventExecutor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class PublicHttpClientTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "0.0.0.0", "0.1.2.3", "10.0.0.1", "127.0.0.1", "169.254.169.254", "172.16.0.1",
        "172.31.255.255", "192.168.0.1", "100.64.0.1", "100.127.255.255", "192.0.0.1",
        "192.0.2.1", "198.18.0.1", "198.19.255.255", "198.51.100.1", "203.0.113.1",
        "224.0.0.1", "255.255.255.255", "::", "::1", "fe80::1", "fd00::1", "ff02::1",
        "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::7f00:1", "2002:7f00:1::",
        "2001:db8::1", "2001::1", "3fff::1"
    })
    void rejectsPrivateReservedAndTransitionAddresses(String address) throws Exception {
        assertThat(PublicHttpClient.isPublicAddress(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "93.184.216.34", "2606:4700:4700::1111",
        "2001:4860:4860::8888"})
    void allowsPublicAddresses(String address) throws Exception {
        assertThat(PublicHttpClient.isPublicAddress(InetAddress.getByName(address))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/a", "http://user:pass@example.com",
        "http://example.com:8080/a", "https://example.com/#fragment", "//example.com/path"})
    void rejectsUnsupportedUriShapes(String uri) {
        assertThatThrownBy(() -> PublicHttpClient.validateUri(URI.create(uri)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDnsWhenAnyAnswerIsPrivate() throws Exception {
        var publicAddress = InetAddress.getByName("1.1.1.1");
        var privateAddress = InetAddress.getByName("127.0.0.1");
        var client = new PublicHttpClient(host -> new InetAddress[] {publicAddress, privateAddress});
        assertThatThrownBy(() -> client.resolvePublicAddresses("rebind.example"))
            .hasMessageContaining("only to public IP");
    }

    @Test
    void transportResolverPinsValidatedAnswersAndRejectsChangedTargets() throws Exception {
        var publicAddress = InetAddress.getByName("1.1.1.1");
        var group = new PublicHttpClient.PinnedAddressResolverGroup("www.example.com", 443,
            List.of(publicAddress));
        var executor = new DefaultEventExecutor();
        try {
            var resolver = group.getResolver(executor);
            var resolved = resolver.resolve(
                InetSocketAddress.createUnresolved("www.example.com", 443)).sync().getNow();
            assertThat(resolved.getAddress()).isEqualTo(publicAddress);
            assertThat(resolved.getPort()).isEqualTo(443);
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("localhost", 443))
                .await().cause()).isInstanceOf(IllegalArgumentException.class);
            assertThat(resolver.resolve(new InetSocketAddress("127.0.0.1", 443))
                .await().cause()).isInstanceOf(IllegalArgumentException.class);
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("www.example.com", 80))
                .await().cause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            group.close();
            executor.shutdownGracefully().sync();
        }
    }

    @Test
    void realTransportPinsTheHostRejectsRedirectsAndBoundsChunkedResponseBytes() throws Exception {
        var redirectedRequests = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0)
            .route(routes -> routes
                .get("/text", (request, response) -> response.header("Content-Type", "text/plain")
                    .sendString(Mono.just("Public text")))
                .get("/redirect", (request, response) -> response.status(302)
                    .header("Location", "/redirect-target").send())
                .get("/redirect-target", (request, response) -> {
                    redirectedRequests.incrementAndGet();
                    return response.sendString(Mono.just("Must not follow"));
                })
                .get("/oversized", (request, response) -> response.header("Content-Type", "text/plain")
                    .sendString(Flux.just("a".repeat(128), "b".repeat(128))))
                .get("/binary", (request, response) -> response
                    .header("Content-Type", "application/octet-stream").sendString(Mono.just("bytes"))))
            .bindNow();
        try {
            var client = new PublicHttpClient();
            var addresses = List.of(InetAddress.getByName("127.0.0.1"));
            // Exercise the transport against a local server with an explicit address list. The
            // public get() entry point rejects this private list; this avoids external test traffic.
            var base = "http://public.example:" + server.port();
            StepVerifier.create(client.exchange(URI.create(base + "/text"), Map.of(), 256,
                    List.of(MediaType.TEXT_PLAIN), addresses))
                .assertNext(response -> assertThat(new String(response.body(),
                    java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("Public text"))
                .verifyComplete();
            StepVerifier.create(client.exchange(URI.create(base + "/redirect"), Map.of(), 256,
                    List.of(MediaType.TEXT_PLAIN), addresses))
                .expectErrorMessage("Redirects are not allowed; use the destination URL directly")
                .verify();
            assertThat(redirectedRequests.get()).isZero();
            StepVerifier.create(client.exchange(URI.create(base + "/oversized"), Map.of(), 128,
                    List.of(MediaType.TEXT_PLAIN), addresses))
                .expectError(org.springframework.core.io.buffer.DataBufferLimitException.class)
                .verify();
            StepVerifier.create(client.exchange(URI.create(base + "/binary"), Map.of(), 256,
                    List.of(MediaType.TEXT_PLAIN), addresses))
                .expectErrorMessage("Unsupported external response content type")
                .verify();
        } finally {
            server.disposeNow();
        }
    }
}
