package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.util.concurrent.DefaultEventExecutor;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
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
    @ValueSource(
            strings = {
                "0.0.0.0",
                "0.1.2.3",
                "10.0.0.1",
                "127.0.0.1",
                "169.254.169.254",
                "172.16.0.1",
                "172.31.255.255",
                "192.168.0.1",
                "100.64.0.1",
                "100.127.255.255",
                "192.0.0.1",
                "192.0.2.1",
                "198.18.0.1",
                "198.19.255.255",
                "198.51.100.1",
                "203.0.113.1",
                "224.0.0.1",
                "255.255.255.255",
                "::",
                "::1",
                "fe80::1",
                "fd00::1",
                "ff02::1",
                "::ffff:127.0.0.1",
                "::ffff:10.0.0.1",
                "64:ff9b::7f00:1",
                "2002:7f00:1::",
                "2001:db8::1",
                "2001::1",
                "3fff::1"
            })
    void rejectsPrivateReservedAndTransitionAddresses(String address) throws Exception {
        assertThat(PublicHttpClient.isPublicAddress(InetAddress.getByName(address)))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "8.8.8.8", "93.184.216.34", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    void allowsPublicAddresses(String address) throws Exception {
        assertThat(PublicHttpClient.isPublicAddress(InetAddress.getByName(address)))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "file:///etc/passwd",
                "ftp://example.com/a",
                "http://user:pass@example.com",
                "http://example.com:8080/a",
                "https://example.com/#fragment",
                "//example.com/path"
            })
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

    @ParameterizedTest
    @ValueSource(
            strings = {"198.18.0.0", "198.19.255.255", "fdfe:dcba:9876::1", "fdfe:dcba:9876:0:ffff:ffff:ffff:ffff"})
    void fakeIpAddressesRequireExplicitCompatibility(String address) throws Exception {
        var ip = InetAddress.getByName(address);
        var client = new PublicHttpClient(host -> new InetAddress[] {ip});
        assertThat(PublicHttpClient.isVpnFakeAddress(ip)).isTrue();
        assertThat(PublicHttpClient.isPublicAddress(ip)).isFalse();
        assertThatThrownBy(() -> client.resolvePublicAddresses("docs.example.com"))
                .hasMessageContaining("VPN Fake-IP");
        assertThat(client.resolveAddresses("docs.example.com", true)).containsExactly(ip);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "198.17.255.255",
                "198.20.0.0",
                "fdfe:dcba:9875::1",
                "fdfe:dcba:9876:1::1",
                "fdfe:dcba:9877::1",
                "127.0.0.1",
                "10.0.0.1"
            })
    void fakeIpRangesDoNotIncludeAdjacentOrPrivateNetworks(String address) throws Exception {
        assertThat(PublicHttpClient.isVpnFakeAddress(InetAddress.getByName(address)))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://docs.example.com/a",
                "https://docs.example.com:80/a",
                "https://198.18.0.4/a",
                "https://[fdfe:dcba:9876::1]/a",
                "https://3323068417/a",
                "https://198.18.1/a",
                "https://198.1179649/a"
            })
    void compatibilityRequiresAHttpsDomainOnPort443(String url) {
        assertThat(PublicHttpClient.allowsVpnFakeIp(URI.create(url), true)).isFalse();
    }

    @Test
    void compatibilityAcceptsAnyHttpsDomainOnlyWhenEnabled() {
        assertThat(PublicHttpClient.allowsVpnFakeIp(URI.create("https://DOCS.EXAMPLE.COM./a"), true))
                .isTrue();
        assertThat(PublicHttpClient.allowsVpnFakeIp(URI.create("https://docs.example.com:443/a"), true))
                .isTrue();
        assertThat(PublicHttpClient.allowsVpnFakeIp(URI.create("https://sub.example.org/a"), true))
                .isTrue();
        assertThat(PublicHttpClient.allowsVpnFakeIp(URI.create("https://docs.example.com/a"), false))
                .isFalse();
    }

    @Test
    void onlyExplicitWebPageRequestsCanConnectToFakeIpAnswers() throws Exception {
        var ipv4 = InetAddress.getByName("198.18.0.4");
        var ipv6 = InetAddress.getByName("fdfe:dcba:9876::46");
        var publicIp = InetAddress.getByName("1.1.1.1");
        var exchanges = new AtomicInteger();
        var uri = URI.create("https://docs.example.com/path?q=value");
        var otherUri = URI.create("https://other.example.org/another-path");
        var client = new PublicHttpClient(host -> new InetAddress[] {ipv4, ipv6, publicIp}) {
            @Override
            Mono<Response> exchange(
                    URI requestedUri,
                    Map<String, String> headers,
                    int maxBytes,
                    List<MediaType> types,
                    List<InetAddress> addresses) {
                exchanges.incrementAndGet();
                assertThat(requestedUri).isIn(uri, otherUri);
                assertThat(addresses).containsExactly(ipv4, ipv6, publicIp);
                return Mono.just(new Response(new byte[0], MediaType.TEXT_HTML));
            }
        };
        StepVerifier.create(client.get(uri, Map.of(), 256, List.of(MediaType.TEXT_HTML), true))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(client.get(otherUri, Map.of(), 256, List.of(MediaType.TEXT_HTML), true))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(client.get(uri, Map.of(), 256, List.of(MediaType.TEXT_HTML)))
                .expectErrorMatches(error -> error.getMessage().contains("VPN Fake-IP"))
                .verify();
        StepVerifier.create(client.get(uri, Map.of(), 256, List.of(MediaType.TEXT_HTML), false))
                .expectErrorMatches(error -> error.getMessage().contains("VPN Fake-IP"))
                .verify();
        assertThat(exchanges).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.1", "169.254.169.254", "fd00::1", "fdfe:dcba:9876:1::1"})
    void compatibilityStillRejectsDnsAnswersContainingOtherPrivateAddresses(String address) throws Exception {
        var fake = InetAddress.getByName("198.18.0.4");
        var other = InetAddress.getByName(address);
        var client = new PublicHttpClient(host -> new InetAddress[] {fake, other});
        StepVerifier.create(client.get(
                        URI.create("https://docs.example.com/"), Map.of(), 256, List.of(MediaType.TEXT_HTML), true))
                .expectErrorMessage("The URL must resolve only to public IP addresses")
                .verify();
    }

    @Test
    void transportResolverPinsValidatedAnswersAndRejectsChangedTargets() throws Exception {
        var publicAddress = InetAddress.getByName("1.1.1.1");
        var group = new PublicHttpClient.PinnedAddressResolverGroup("www.example.com", 443, List.of(publicAddress));
        var executor = new DefaultEventExecutor();
        try {
            var resolver = group.getResolver(executor);
            var resolved = resolver.resolve(InetSocketAddress.createUnresolved("www.example.com", 443))
                    .sync()
                    .getNow();
            assertThat(resolved.getAddress()).isEqualTo(publicAddress);
            assertThat(resolved.getPort()).isEqualTo(443);
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("localhost", 443))
                            .await()
                            .cause())
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(resolver.resolve(new InetSocketAddress("127.0.0.1", 443))
                            .await()
                            .cause())
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("www.example.com", 80))
                            .await()
                            .cause())
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            group.close();
            executor.shutdownGracefully().sync();
        }
    }

    @Test
    void realTransportPinsTheHostRejectsRedirectsAndBoundsChunkedResponseBytes() throws Exception {
        var redirectedRequests = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get(
                                "/text",
                                (request, response) -> response.header("Content-Type", "text/plain")
                                        .sendString(Mono.just("Public text")))
                        .get(
                                "/redirect",
                                (request, response) -> response.status(302)
                                        .header("Location", "/redirect-target")
                                        .send())
                        .get("/redirect-target", (request, response) -> {
                            redirectedRequests.incrementAndGet();
                            return response.sendString(Mono.just("Must not follow"));
                        })
                        .get(
                                "/oversized",
                                (request, response) -> response.header("Content-Type", "text/plain")
                                        .sendString(Flux.just("a".repeat(128), "b".repeat(128))))
                        .get(
                                "/binary",
                                (request, response) -> response.header("Content-Type", "application/octet-stream")
                                        .sendString(Mono.just("bytes"))))
                .bindNow();
        try {
            var client = new PublicHttpClient();
            var addresses = List.of(InetAddress.getByName("127.0.0.1"));
            // Exercise the transport against a local server with an explicit address list. The
            // public get() entry point rejects this private list; this avoids external test traffic.
            var base = "http://public.example:" + server.port();
            StepVerifier.create(client.exchange(
                            URI.create(base + "/text"), Map.of(), 256, List.of(MediaType.TEXT_PLAIN), addresses))
                    .assertNext(
                            response -> assertThat(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8))
                                    .isEqualTo("Public text"))
                    .verifyComplete();
            StepVerifier.create(client.exchange(
                            URI.create(base + "/redirect"), Map.of(), 256, List.of(MediaType.TEXT_PLAIN), addresses))
                    .expectErrorMessage("Redirects are not allowed; use the destination URL directly")
                    .verify();
            assertThat(redirectedRequests.get()).isZero();
            StepVerifier.create(client.exchange(
                            URI.create(base + "/oversized"), Map.of(), 128, List.of(MediaType.TEXT_PLAIN), addresses))
                    .expectError(org.springframework.core.io.buffer.DataBufferLimitException.class)
                    .verify();
            StepVerifier.create(client.exchange(
                            URI.create(base + "/binary"), Map.of(), 256, List.of(MediaType.TEXT_PLAIN), addresses))
                    .expectErrorMessage("Unsupported external response content type")
                    .verify();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void textRedirectsRevalidateEveryHostAndReportTheFinalFragmentFreeUri() throws Exception {
        var resolvedHosts = new CopyOnWriteArrayList<String>();
        var publicIp = InetAddress.getByName("1.1.1.1");
        var client = new ScriptedTextClient(
                host -> {
                    resolvedHosts.add(host);
                    return new InetAddress[] {publicIp};
                },
                uri -> switch (uri.getPath()) {
                    case "/start" -> redirect("https://other.example/next#section");
                    case "/next" -> redirect("https://other.example/final");
                    default -> textResponse();
                });
        StepVerifier.create(client.getText(URI.create("https://first.example/start#ignored"), 256, false))
                .assertNext(response -> assertThat(response.uri()).isEqualTo(URI.create("https://other.example/final")))
                .verifyComplete();
        assertThat(resolvedHosts).containsExactly("first.example", "other.example", "other.example");
        assertThat(client.exchanges)
                .containsExactly(
                        URI.create("https://first.example/start"),
                        URI.create("https://other.example/next"),
                        URI.create("https://other.example/final"));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6})
    void textReadsFollowAtMostFiveRedirects(int redirects) throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        var client = new ScriptedTextClient(host -> new InetAddress[] {publicIp}, uri -> {
            var step = Integer.parseInt(uri.getPath().substring(1));
            return step == redirects ? textResponse() : redirect("https://example.com/" + (step + 1));
        });
        var result = StepVerifier.create(client.getText(URI.create("https://example.com/0"), 256, false));
        if (redirects == 5) {
            result.assertNext(response -> assertThat(response.uri()).isEqualTo(URI.create("https://example.com/5")))
                    .verifyComplete();
        } else {
            result.expectErrorMessage("External HTTP response exceeded the redirect limit")
                    .verify();
        }
        assertThat(client.exchanges).hasSize(6);
    }

    @Test
    void textReadsRejectEquivalentUriLoopsBeforeAnotherConnection() throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        var client = new ScriptedTextClient(
                host -> new InetAddress[] {publicIp}, uri -> redirect("https://EXAMPLE.COM:443/a#fragment"));
        StepVerifier.create(client.getText(URI.create("https://example.com/a"), 256, false))
                .expectErrorMessage("External HTTP redirect loop")
                .verify();
        assertThat(client.exchanges).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private", "mixed"})
    void textRedirectsRejectPrivateTargetsAndMixedDnsAnswers(String answer) throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        var privateIp = InetAddress.getByName("169.254.169.254");
        var resolvedHosts = new CopyOnWriteArrayList<String>();
        var client = new ScriptedTextClient(
                host -> {
                    resolvedHosts.add(host);
                    return "first.example".equals(host)
                            ? new InetAddress[] {publicIp}
                            : "mixed".equals(answer)
                                    ? new InetAddress[] {publicIp, privateIp}
                                    : new InetAddress[] {privateIp};
                },
                uri -> redirect("https://target.example/private"));
        StepVerifier.create(client.getText(URI.create("https://first.example/start"), 256, true))
                .expectErrorMessage("The URL must resolve only to public IP addresses")
                .verify();
        assertThat(resolvedHosts).containsExactly("first.example", "target.example");
        assertThat(client.exchanges).hasSize(1);
    }

    @Test
    void textRedirectsResolveTheSameHostAgainInsteadOfReusingItsPreviousAnswers() throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        var privateIp = InetAddress.getByName("127.0.0.1");
        var lookups = new AtomicInteger();
        var client = new ScriptedTextClient(
                host -> new InetAddress[] {lookups.incrementAndGet() == 1 ? publicIp : privateIp},
                uri -> redirect("https://example.com/next"));
        StepVerifier.create(client.getText(URI.create("https://example.com/start"), 256, false))
                .expectErrorMessage("The URL must resolve only to public IP addresses")
                .verify();
        assertThat(lookups).hasValue(2);
        assertThat(client.exchanges).hasSize(1);
    }

    @Test
    void textRedirectsNeverDowngradeHttpsToHttp() throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        var lookups = new AtomicInteger();
        var client = new ScriptedTextClient(
                host -> {
                    lookups.incrementAndGet();
                    return new InetAddress[] {publicIp};
                },
                uri -> redirect("http://target.example/plain"));
        StepVerifier.create(client.getText(URI.create("https://example.com/start"), 256, true))
                .expectErrorMessage("HTTPS redirects to HTTP are not allowed")
                .verify();
        assertThat(lookups).hasValue(1);
        assertThat(client.exchanges).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.com/plain",
                "https://example.com:80/plain",
                "https://198.18.0.4/plain",
                "https://3323068417/plain",
                "https://[fdfe:dcba:9876::1]/plain"
            })
    void textCompatibilityStillRequiresAnHttpsDomainOnEveryHop(String url) throws Exception {
        var fakeIp = InetAddress.getByName("198.18.0.4");
        var client = new ScriptedTextClient(host -> new InetAddress[] {fakeIp}, uri -> redirect(url));
        StepVerifier.create(client.getText(URI.create("https://example.com/start"), 256, true))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(client.exchanges).hasSize(1);
    }

    @Test
    void textTimeoutIncludesTheWholeRedirectChain() throws Exception {
        var publicIp = InetAddress.getByName("1.1.1.1");
        var client = new ScriptedTextClient(
                host -> new InetAddress[] {publicIp},
                uri -> "/start".equals(uri.getPath())
                        ? Mono.delay(Duration.ofSeconds(5)).then(redirect("https://example.com/final"))
                        : Mono.delay(Duration.ofSeconds(8)).then(textResponse()));
        StepVerifier.withVirtualTime(() -> client.getText(URI.create("https://example.com/start"), 256, false))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(11))
                .thenAwait(Duration.ofSeconds(1))
                .expectErrorMessage("External HTTP request failed")
                .verify();
    }

    @Test
    void textTransportResolvesRelativeProtocolRelativeAndQueryLocationsWithoutAutoFollowing() throws Exception {
        var finalRequests = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get(
                                "/redirect/{status}",
                                (request, response) -> response.status(Integer.parseInt(request.param("status")))
                                        .header("Location", "../final?mode=raw#section")
                                        .send())
                        .get(
                                "/protocol",
                                (request, response) -> response.status(307)
                                        .header("Location", "//other.example/final#section")
                                        .send())
                        .get(
                                "/query",
                                (request, response) -> response.status(303)
                                        .header("Location", "?page=2#section")
                                        .send())
                        .get(
                                "/missing",
                                (request, response) -> response.status(308).send())
                        .get(
                                "/invalid",
                                (request, response) -> response.status(301)
                                        .header("Location", "http://[invalid")
                                        .send())
                        .get("/final", (request, response) -> {
                            finalRequests.incrementAndGet();
                            return response.send();
                        }))
                .bindNow();
        try {
            var client = new PublicHttpClient();
            var addresses = List.of(InetAddress.getByName("127.0.0.1"));
            var base = "http://public.example:" + server.port();
            for (var status : List.of(301, 302, 303, 307, 308)) {
                StepVerifier.create(client.exchangeText(URI.create(base + "/redirect/" + status), 256, addresses))
                        .expectErrorSatisfies(error -> assertThat(error)
                                .isInstanceOfSatisfying(
                                        PublicHttpClient.RedirectException.class,
                                        redirect -> assertThat(redirect.uri())
                                                .isEqualTo(URI.create(base + "/final?mode=raw"))))
                        .verify();
            }
            StepVerifier.create(client.exchangeText(URI.create(base + "/protocol"), 256, addresses))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOfSatisfying(
                                    PublicHttpClient.RedirectException.class,
                                    redirect -> assertThat(redirect.uri())
                                            .isEqualTo(URI.create("http://other.example/final"))))
                    .verify();
            StepVerifier.create(client.exchangeText(URI.create(base + "/query?old=value"), 256, addresses))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOfSatisfying(
                                    PublicHttpClient.RedirectException.class,
                                    redirect ->
                                            assertThat(redirect.uri()).isEqualTo(URI.create(base + "/query?page=2"))))
                    .verify();
            for (var path : List.of("/missing", "/invalid")) {
                StepVerifier.create(client.exchangeText(URI.create(base + path), 256, addresses))
                        .expectErrorMessage("External HTTP redirect has no valid Location")
                        .verify();
            }
            assertThat(finalRequests).hasValue(0);
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void textTransportAcceptsTextFormatsAndUnknownBodiesButRejectsExplicitBinaryTypes() throws Exception {
        var contentType = new AtomicReference<String>();
        var receivedHeaders = new AtomicReference<io.netty.handler.codec.http.HttpHeaders>();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/text", (request, response) -> {
                    receivedHeaders.set(request.requestHeaders().copy());
                    response.header("Set-Cookie", "must-not-be-replayed=1");
                    if (contentType.get() != null) {
                        response.header("Content-Type", contentType.get());
                    }
                    return response.sendByteArray(Mono.just("Hello".getBytes(StandardCharsets.UTF_8)));
                }))
                .bindNow();
        try {
            var client = new PublicHttpClient();
            var addresses = List.of(InetAddress.getByName("127.0.0.1"));
            var uri = URI.create("http://public.example:" + server.port() + "/text");
            for (var type : Arrays.asList(
                    null,
                    "application/octet-stream",
                    "text/markdown",
                    "application/json; charset=UTF-8",
                    "application/problem+json",
                    "application/x-ndjson",
                    "application/json-seq",
                    "application/xml",
                    "application/rss+xml",
                    "application/atom+xml",
                    "application/yaml",
                    "application/x-toml",
                    "application/javascript",
                    "application/ecmascript",
                    "application/sql",
                    "application/graphql",
                    "application/x-www-form-urlencoded",
                    "application/x-python")) {
                contentType.set(type);
                StepVerifier.create(client.exchangeText(uri, 256, addresses))
                        .assertNext(response -> {
                            assertThat(new String(response.body(), StandardCharsets.UTF_8))
                                    .isEqualTo("Hello");
                            assertThat(response.uri()).isEqualTo(uri);
                        })
                        .verifyComplete();
                var headers = receivedHeaders.get();
                assertThat(headers.get("User-Agent")).isEqualTo("Halo-AI-Foundation/1.0");
                assertThat(headers.get("Accept")).contains("text/*", "application/json");
                assertThat(headers.get("Authorization")).isNull();
                assertThat(headers.get("Cookie")).isNull();
                assertThat(headers.get("X-Subscription-Token")).isNull();
            }
            for (var type : List.of("application/pdf", "image/png", "image/svg+xml", "application/zip")) {
                contentType.set(type);
                StepVerifier.create(client.exchangeText(uri, 256, addresses))
                        .expectErrorMessage("Unsupported external response content type")
                        .verify();
            }
            contentType.set("text/plain");
            StepVerifier.create(client.exchange(
                            uri,
                            Map.of("Accept", "application/json", "X-Subscription-Token", "test-only-api-key"),
                            256,
                            List.of(MediaType.APPLICATION_JSON),
                            addresses))
                    .expectErrorMessage("Unsupported external response content type")
                    .verify();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void transportDecompressesGzipAndBoundsTheExpandedBodyIncludingChunkedCompression() throws Exception {
        var small = "Compressed public text".getBytes(StandardCharsets.UTF_8);
        var compressedSmall = gzip(small);
        var bomb = gzip("x".repeat(16_384).getBytes(StandardCharsets.UTF_8));
        assertThat(bomb.length).isLessThan(128);
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get(
                                "/small",
                                (request, response) -> response.header("Content-Type", "text/plain")
                                        .header("Content-Encoding", "gzip")
                                        .header("Content-Length", Integer.toString(compressedSmall.length))
                                        .sendByteArray(Mono.just(compressedSmall)))
                        .get(
                                "/bomb",
                                (request, response) -> response.header("Content-Type", "text/plain")
                                        .header("Content-Encoding", "gzip")
                                        .header("Content-Length", Integer.toString(bomb.length))
                                        .sendByteArray(Mono.just(bomb)))
                        .get(
                                "/chunked-bomb",
                                (request, response) -> response.header("Content-Type", "text/plain")
                                        .header("Content-Encoding", "gzip")
                                        .sendByteArray(Flux.just(
                                                Arrays.copyOfRange(bomb, 0, 12),
                                                Arrays.copyOfRange(bomb, 12, bomb.length)))))
                .bindNow();
        try {
            var client = new PublicHttpClient();
            var addresses = List.of(InetAddress.getByName("127.0.0.1"));
            var base = "http://public.example:" + server.port();
            StepVerifier.create(client.exchangeText(URI.create(base + "/small"), 128, addresses))
                    .assertNext(response -> assertThat(response.body()).isEqualTo(small))
                    .verifyComplete();
            for (var path : List.of("/bomb", "/chunked-bomb")) {
                StepVerifier.create(client.exchangeText(URI.create(base + path), 128, addresses))
                        .expectError(org.springframework.core.io.buffer.DataBufferLimitException.class)
                        .verify();
            }
        } finally {
            server.disposeNow();
        }
    }

    private static Mono<PublicHttpClient.Response> textResponse() {
        return Mono.just(new PublicHttpClient.Response("text".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));
    }

    private static Mono<PublicHttpClient.Response> redirect(String uri) {
        return Mono.error(new PublicHttpClient.RedirectException(URI.create(uri)));
    }

    private static byte[] gzip(byte[] bytes) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }

    private static final class ScriptedTextClient extends PublicHttpClient {
        private final Function<URI, Mono<Response>> response;
        private final List<URI> exchanges = new CopyOnWriteArrayList<>();

        ScriptedTextClient(HostResolver resolver, Function<URI, Mono<Response>> response) {
            super(resolver);
            this.response = response;
        }

        @Override
        Mono<Response> exchangeText(URI uri, int maxBytes, List<InetAddress> addresses) {
            exchanges.add(uri);
            return response.apply(uri);
        }
    }
}
