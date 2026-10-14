package run.halo.aifoundation.tool.builtin;

import io.netty.channel.ChannelOption;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/** A bounded, cookie-free HTTP client that connects only to validated public addresses. */
@Component
public class PublicHttpClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private final HostResolver hostResolver;

    public PublicHttpClient() {
        this(InetAddress::getAllByName);
    }

    PublicHttpClient(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    Mono<Response> get(URI uri, Map<String, String> headers, int maxBytes,
        List<MediaType> allowedContentTypes) {
        return Mono.defer(() -> {
            validateUri(uri);
            return Mono.fromCallable(() -> resolvePublicAddresses(uri.getHost()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(addresses -> exchange(uri, headers, maxBytes, allowedContentTypes,
                    addresses));
        }).timeout(REQUEST_TIMEOUT).onErrorMap(error -> {
            if (error instanceof RequestRejectedException) {
                return error;
            }
            if (error instanceof DataBufferLimitException) {
                return new IllegalArgumentException("External response exceeds the size limit");
            }
            // Never expose response bodies, request headers or third-party exception messages.
            return new IllegalStateException("External HTTP request failed");
        });
    }

    Mono<Response> exchange(URI uri, Map<String, String> headers, int maxBytes,
        List<MediaType> allowedContentTypes, List<InetAddress> addresses) {
        var resolver = new PinnedAddressResolverGroup(uri.getHost(), port(uri), addresses);
        // A fresh connection also prevents a connection pool from bypassing DNS validation.
        var httpClient = HttpClient.newConnection()
            .resolver(resolver)
            .followRedirect(false)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .responseTimeout(Duration.ofSeconds(8));
        var client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxBytes))
            .build();
        return client.get().uri(uri)
            .headers(requestHeaders -> {
                requestHeaders.set("User-Agent", "Halo-AI-Foundation/1.0");
                headers.forEach(requestHeaders::set);
            })
            .exchangeToMono(response -> {
                var status = response.statusCode();
                if (!status.is2xxSuccessful()) {
                    return response.releaseBody().then(Mono.error(new RequestRejectedException(
                        status.is3xxRedirection()
                            ? "Redirects are not allowed; use the destination URL directly"
                            : "External HTTP request returned status " + status.value())));
                }
                var contentType = response.headers().contentType().orElse(null);
                if (contentType == null || allowedContentTypes.stream()
                    .noneMatch(type -> type.isCompatibleWith(contentType))) {
                    return response.releaseBody().then(Mono.error(new RequestRejectedException(
                        "Unsupported external response content type")));
                }
                if (response.headers().contentLength().orElse(-1) > maxBytes) {
                    return response.releaseBody().then(Mono.error(new RequestRejectedException(
                        "External response exceeds the size limit")));
                }
                return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                    .map(body -> new Response(body, contentType));
            }).doFinally(signal -> resolver.close());
    }

    static void validateUri(URI uri) {
        if (uri == null || uri.toString().length() > 8_192 || !uri.isAbsolute()
            || uri.getHost() == null || uri.getHost().isBlank()
            || uri.getRawUserInfo() != null || uri.getRawFragment() != null
            || !("http".equalsIgnoreCase(uri.getScheme())
            || "https".equalsIgnoreCase(uri.getScheme()))
            || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)) {
            throw new RequestRejectedException("Use a public HTTP(S) URL on port 80 or 443 "
                + "without credentials or a fragment");
        }
    }

    List<InetAddress> resolvePublicAddresses(String host) throws UnknownHostException {
        var addresses = hostResolver.resolve(host);
        if (addresses == null || addresses.length == 0
            || Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
            throw new RequestRejectedException("The URL must resolve only to public IP addresses");
        }
        return List.copyOf(Arrays.asList(addresses));
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            return first != 0 && first != 10 && first != 127 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168)
                && !(first == 192 && second == 0 && (third == 0 || third == 2))
                && !(first == 192 && second == 88 && third == 99)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113);
        }
        // Permit ordinary global unicast IPv6, rejecting transition networks that can encode
        // private IPv4 targets, protocol assignments, and documentation ranges.
        if (bytes.length != 16 || (bytes[0] & 0xe0) != 0x20) {
            return false;
        }
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;
        return !(bytes[0] == 0x20 && second == 0x02)
            && !(bytes[0] == 0x20 && second == 0x01 && third <= 1)
            && !(bytes[0] == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8)
            && !(bytes[0] == 0x3f && second == 0xff && (third & 0xf0) == 0);
    }

    static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort()
            : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    record Response(byte[] body, MediaType contentType) {
    }

    private static final class RequestRejectedException extends IllegalArgumentException {
        RequestRejectedException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    static final class PinnedAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
        private final String host;
        private final int port;
        private final List<InetAddress> addresses;

        PinnedAddressResolverGroup(String host, int port, List<InetAddress> addresses) {
            this.host = host.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
            this.port = port;
            this.addresses = addresses;
        }

        @Override
        protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
            return new AbstractAddressResolver<>(executor) {
                @Override
                protected boolean doIsResolved(InetSocketAddress address) {
                    // Resolved literals must pass through the same pinned-address check.
                    return false;
                }

                @Override
                protected void doResolve(InetSocketAddress address,
                    Promise<InetSocketAddress> promise) {
                    if (matches(address)) {
                        promise.setSuccess(new InetSocketAddress(addresses.getFirst(), port));
                    } else {
                        promise.setFailure(new IllegalArgumentException("Unexpected HTTP target"));
                    }
                }

                @Override
                protected void doResolveAll(InetSocketAddress address,
                    Promise<List<InetSocketAddress>> promise) {
                    if (matches(address)) {
                        promise.setSuccess(addresses.stream()
                            .map(ip -> new InetSocketAddress(ip, port)).toList());
                    } else {
                        promise.setFailure(new IllegalArgumentException("Unexpected HTTP target"));
                    }
                }

                private boolean matches(InetSocketAddress address) {
                    return address.getPort() == port && (host.equals(address.getHostString()
                        .replace("[", "").replace("]", "").toLowerCase(Locale.ROOT))
                        || address.getAddress() != null && addresses.contains(address.getAddress()));
                }
            };
        }
    }
}
