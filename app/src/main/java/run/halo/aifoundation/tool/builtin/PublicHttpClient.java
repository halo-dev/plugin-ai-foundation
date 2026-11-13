package run.halo.aifoundation.tool.builtin;

import io.netty.channel.ChannelOption;
import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/**
 * Downloads bounded public HTTP resources using validated, pinned DNS addresses.
 *
 * <p>Requests use fresh connections, enforce response limits and do not inherit the caller's cookies or authorization.
 * Redirects are validated individually. HTTPS Fake-IP compatibility is opt-in and does not grant general access to
 * private networks.
 *
 * <pre>{@code
 * http.getText(URI.create("https://example.com/data.json"), 10 * 1_048_576, false);
 * }</pre>
 *
 * <p>The local attachment method is a separate, narrowly scoped path for an already authorized Halo attachment.
 * Arbitrary caller URLs must use the public download methods.
 */
@Component
public class PublicHttpClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final String TEXT_ACCEPT = "text/*,application/json,application/xml,"
            + "application/xhtml+xml,application/yaml,application/toml,application/javascript,"
            + "application/ecmascript,application/graphql,application/rss+xml,application/atom+xml,"
            + "application/x-www-form-urlencoded,*/*;q=0.1";
    private static final Set<String> TEXT_APPLICATION_SUBTYPES = Set.of(
            "json",
            "ndjson",
            "x-ndjson",
            "jsonl",
            "json-seq",
            "xml",
            "yaml",
            "x-yaml",
            "toml",
            "x-toml",
            "javascript",
            "ecmascript",
            "typescript",
            "x-javascript",
            "sql",
            "x-sql",
            "x-sh",
            "x-shellscript",
            "x-python",
            "x-ruby",
            "x-perl",
            "x-php",
            "x-httpd-php",
            "x-csh",
            "x-latex",
            "graphql",
            "csv",
            "x-csv",
            "x-www-form-urlencoded");
    private final HostResolver hostResolver;

    /** Creates a client that resolves hostnames through the JVM's configured DNS resolver. */
    public PublicHttpClient() {
        this(InetAddress::getAllByName);
    }

    PublicHttpClient(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    Mono<Response> get(URI uri, Map<String, String> headers, int maxBytes, List<MediaType> allowedContentTypes) {
        return get(uri, headers, maxBytes, allowedContentTypes, false);
    }

    Mono<Response> get(
            URI uri,
            Map<String, String> headers,
            int maxBytes,
            List<MediaType> allowedContentTypes,
            boolean vpnCompatibilityEnabled) {
        return Mono.defer(() -> {
                    validateUri(uri);
                    var allowFakeIp = allowsVpnFakeIp(uri, vpnCompatibilityEnabled);
                    return Mono.fromCallable(() -> resolveAddresses(uri.getHost(), allowFakeIp))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(addresses -> exchange(uri, headers, maxBytes, allowedContentTypes, addresses));
                })
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(PublicHttpClient::safeError);
    }

    Mono<Response> getText(URI uri, int maxBytes, boolean vpnCompatibilityEnabled) {
        // The single deadline includes every DNS lookup, response and redirect in the chain.
        return Mono.defer(() -> followDownload(uri, maxBytes, vpnCompatibilityEnabled, new HashSet<>(), 0, true))
                .timeout(REQUEST_TIMEOUT)
                .onErrorMap(PublicHttpClient::safeError);
    }

    /**
     * Downloads public binary data without forwarding caller headers or cookies.
     *
     * @param uri public HTTP(S) destination
     * @param maxBytes maximum decompressed response size
     * @param vpnCompatibilityEnabled whether trusted HTTPS Fake-IP resolution is allowed
     * @return bounded bytes, response media type and the final validated URI
     */
    Mono<Response> getResource(URI uri, int maxBytes, boolean vpnCompatibilityEnabled) {
        return Mono.defer(() -> followDownload(uri, maxBytes, vpnCompatibilityEnabled, new HashSet<>(), 0, false))
                .timeout(DOWNLOAD_TIMEOUT)
                .onErrorMap(PublicHttpClient::safeError);
    }

    /**
     * Downloads an already authorized attachment from Halo's local public upload route.
     *
     * <p>The caller must check the attachment permission and derive the location from Halo's storage service. Only
     * {@code /upload/} paths on the current request's local listener are accepted; credentials and redirects are never
     * forwarded.
     *
     * @param location attachment location supplied by Halo, not an arbitrary tool argument
     * @param maxBytes maximum decompressed response size
     * @return bounded attachment bytes and media type
     */
    Mono<Response> getLocalAttachment(URI location, int maxBytes) {
        return Mono.<Response>deferContextual(context -> {
                    var exchange = ServerWebExchangeContextFilter.getExchange(context)
                            .orElseThrow(() -> new RequestRejectedException(
                                    "Local attachments require the current Halo HTTP request"));
                    var address = exchange.getRequest().getLocalAddress();
                    if (address == null || address.getPort() <= 0 || maxBytes <= 0) {
                        throw new RequestRejectedException("Halo's local attachment listener is unavailable");
                    }
                    validateLocalAttachmentLocation(location, address.getPort());
                    var scheme = exchange.getRequest().getSslInfo() == null ? "http" : "https";
                    var target = URI.create(scheme + "://127.0.0.1:" + address.getPort() + location.getRawPath());
                    // No credentials and no redirects, including redirects back into authenticated APIs.
                    return exchangeResource(target, maxBytes, List.of(InetAddress.getLoopbackAddress()));
                })
                .timeout(DOWNLOAD_TIMEOUT)
                .onErrorMap(PublicHttpClient::safeError);
    }

    private static void validateLocalAttachmentLocation(URI location, int localPort) {
        if (location.getRawUserInfo() != null || location.getRawQuery() != null || location.getRawFragment() != null) {
            throw new RequestRejectedException("Unsupported local attachment URL");
        }
        var path = location.getPath();
        if (path == null || !path.startsWith("/upload/")) {
            throw new RequestRejectedException("Unsupported local attachment URL");
        }
        if (path.indexOf('\\') >= 0
                || path.chars().anyMatch(Character::isISOControl)
                || Arrays.stream(path.split("/", -1)).anyMatch(part -> part.equals(".") || part.equals(".."))) {
            throw new RequestRejectedException("Unsupported local attachment URL");
        }
        if (!location.isAbsolute()) {
            if (location.getRawAuthority() != null) {
                throw new RequestRejectedException("Unsupported local attachment URL");
            }
            return;
        }
        if (!localAttachmentHost(location.getHost()) || port(location) != localPort || !httpScheme(location)) {
            throw new RequestRejectedException("Unsupported local attachment URL");
        }
    }

    static boolean localAttachmentHost(String host) {
        return host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host.toLowerCase(Locale.ROOT));
    }

    static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException error) {
            // URI parser messages include the complete input, which can contain a signed query.
            throw new IllegalArgumentException("Invalid URL");
        }
    }

    private Mono<Response> followDownload(
            URI requestedUri,
            int maxBytes,
            boolean vpnCompatibilityEnabled,
            Set<String> visited,
            int redirects,
            boolean text) {
        return Mono.defer(() -> {
            var uri = withoutFragment(requestedUri);
            validateUri(uri);
            if (maxBytes <= 0) {
                return Mono.error(new RequestRejectedException("External response size limit must be positive"));
            }
            if (!visited.add(requestKey(uri))) {
                return Mono.error(new RequestRejectedException("External HTTP redirect loop"));
            }
            var allowFakeIp = allowsVpnFakeIp(uri, vpnCompatibilityEnabled);
            return Mono.fromCallable(() -> resolveAddresses(uri.getHost(), allowFakeIp))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(addresses ->
                            text ? exchangeText(uri, maxBytes, addresses) : exchangeResource(uri, maxBytes, addresses))
                    .map(response -> new Response(response.body(), response.contentType(), uri))
                    .onErrorResume(RedirectException.class, redirect -> {
                        if (redirects >= MAX_REDIRECTS) {
                            return Mono.error(
                                    new RequestRejectedException("External HTTP response exceeded the redirect limit"));
                        }
                        if ("https".equalsIgnoreCase(uri.getScheme())
                                && "http".equalsIgnoreCase(redirect.uri.getScheme())) {
                            return Mono.error(new RequestRejectedException("HTTPS redirects to HTTP are not allowed"));
                        }
                        return followDownload(
                                redirect.uri, maxBytes, vpnCompatibilityEnabled, visited, redirects + 1, text);
                    });
        });
    }

    private static Throwable safeError(Throwable error) {
        if (error instanceof RequestRejectedException) {
            return error;
        }
        if (error instanceof DataBufferLimitException) {
            return new IllegalArgumentException("External response exceeds the size limit");
        }
        // Never expose response bodies, request headers, Location or third-party error messages.
        return new IllegalStateException("External HTTP request failed");
    }

    Mono<Response> exchange(
            URI uri,
            Map<String, String> headers,
            int maxBytes,
            List<MediaType> allowedContentTypes,
            List<InetAddress> addresses) {
        return exchange(uri, headers, maxBytes, allowedContentTypes, addresses, false);
    }

    Mono<Response> exchangeText(URI uri, int maxBytes, List<InetAddress> addresses) {
        return exchange(uri, Map.of("Accept", TEXT_ACCEPT), maxBytes, List.of(), addresses, true);
    }

    Mono<Response> exchangeResource(URI uri, int maxBytes, List<InetAddress> addresses) {
        return exchange(uri, Map.of("Accept", "*/*"), maxBytes, List.of(), addresses, false, true);
    }

    private Mono<Response> exchange(
            URI uri,
            Map<String, String> headers,
            int maxBytes,
            List<MediaType> allowedContentTypes,
            List<InetAddress> addresses,
            boolean text) {
        return exchange(uri, headers, maxBytes, allowedContentTypes, addresses, text, false);
    }

    private Mono<Response> exchange(
            URI uri,
            Map<String, String> headers,
            int maxBytes,
            List<MediaType> allowedContentTypes,
            List<InetAddress> addresses,
            boolean text,
            boolean binary) {
        var resolver = new PinnedAddressResolverGroup(uri.getHost(), port(uri), addresses);
        // A fresh connection also prevents a connection pool from bypassing DNS validation.
        var httpClient = HttpClient.newConnection()
                .resolver(resolver)
                .followRedirect(false)
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(8));
        var client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxBytes))
                .build();
        return client.get()
                .uri(uri)
                .headers(requestHeaders -> {
                    requestHeaders.set("User-Agent", "Halo-AI-Foundation/1.0");
                    headers.forEach(requestHeaders::set);
                })
                .exchangeToMono(response -> {
                    var status = response.statusCode();
                    if ((text || binary) && REDIRECT_STATUSES.contains(status.value())) {
                        var next = redirectUri(
                                uri, response.headers().asHttpHeaders().getFirst("Location"));
                        return response.releaseBody().then(Mono.error(new RedirectException(next)));
                    }
                    if (!status.is2xxSuccessful()) {
                        return response.releaseBody()
                                .then(Mono.error(new RequestRejectedException(
                                        !text && status.is3xxRedirection()
                                                ? "Redirects are not allowed; use the destination URL directly"
                                                : "External HTTP request returned status " + status.value())));
                    }
                    var contentType = response.headers().contentType().orElse(null);
                    var supported = binary
                            || (text
                                    ? supportsTextResponse(contentType)
                                    : contentType != null
                                            && allowedContentTypes.stream()
                                                    .anyMatch(type -> type.isCompatibleWith(contentType)));
                    if (!supported) {
                        return response.releaseBody()
                                .then(Mono.error(
                                        new RequestRejectedException("Unsupported external response content type")));
                    }
                    if (response.headers().contentLength().orElse(-1) > maxBytes) {
                        return response.releaseBody()
                                .then(Mono.error(
                                        new RequestRejectedException("External response exceeds the size limit")));
                    }
                    return response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(body -> new Response(body, contentType, uri));
                })
                .doFinally(signal -> resolver.close());
    }

    private static boolean supportsTextResponse(MediaType type) {
        if (type == null) {
            return true;
        }
        if ("text".equalsIgnoreCase(type.getType())) {
            return true;
        }
        var subtype = type.getSubtype().toLowerCase(Locale.ROOT);
        return "application".equalsIgnoreCase(type.getType())
                && ("octet-stream".equals(subtype)
                        || TEXT_APPLICATION_SUBTYPES.contains(subtype)
                        || subtype.endsWith("+json")
                        || subtype.endsWith("+xml")
                        || subtype.endsWith("+yaml")
                        || subtype.endsWith("+toml"));
    }

    private static URI redirectUri(URI current, String location) {
        if (location == null || location.isBlank() || location.length() > 8_192) {
            throw new RequestRejectedException("External HTTP redirect has no valid Location");
        }
        try {
            var reference = URI.create(location.strip());
            URI next;
            if (reference.getScheme() == null
                    && reference.getRawAuthority() == null
                    && (reference.getRawPath() == null || reference.getRawPath().isEmpty())) {
                // URI.resolve otherwise discards the last path segment for a query-only Location.
                var base = withoutFragment(current).toString();
                if (reference.getRawQuery() != null) {
                    var query = base.indexOf('?');
                    base = (query == -1 ? base : base.substring(0, query)) + "?" + reference.getRawQuery();
                }
                next = URI.create(base);
            } else {
                next = current.resolve(reference);
            }
            return withoutFragment(next).normalize();
        } catch (IllegalArgumentException error) {
            throw new RequestRejectedException("External HTTP redirect has no valid Location");
        }
    }

    private static URI withoutFragment(URI uri) {
        if (uri == null || uri.getRawFragment() == null) {
            return uri;
        }
        var value = uri.toString();
        return URI.create(value.substring(0, value.indexOf('#')));
    }

    private static String requestKey(URI uri) {
        var host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        var path = uri.normalize().getRawPath();
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host + ":" + port(uri)
                + (path == null || path.isEmpty() ? "/" : path)
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    static void validateUri(URI uri) {
        if (uri == null || uri.toString().length() > 8_192 || !uri.isAbsolute()) {
            throw invalidPublicUri();
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalidPublicUri();
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw invalidPublicUri();
        }
        if (!httpScheme(uri)) {
            throw invalidPublicUri();
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw invalidPublicUri();
        }
    }

    private static boolean httpScheme(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private static RequestRejectedException invalidPublicUri() {
        return new RequestRejectedException(
                "Use a public HTTP(S) URL on port 80 or 443 " + "without credentials or a fragment");
    }

    List<InetAddress> resolvePublicAddresses(String host) throws UnknownHostException {
        return resolveAddresses(host, false);
    }

    List<InetAddress> resolveAddresses(String host, boolean allowFakeIp) throws UnknownHostException {
        var addresses = hostResolver.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new RequestRejectedException("The URL must resolve only to public IP addresses");
        }
        if (Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address) && !isVpnFakeAddress(address))) {
            throw new RequestRejectedException("The URL must resolve only to public IP addresses");
        }
        if (!allowFakeIp && Arrays.stream(addresses).anyMatch(PublicHttpClient::isVpnFakeAddress)) {
            throw new RequestRejectedException("The URL resolves to VPN Fake-IP addresses. "
                    + "Enable this tool's VPN compatibility mode, "
                    + "or exclude the domain from your VPN's Fake-IP DNS");
        }
        return List.copyOf(Arrays.asList(addresses));
    }

    static boolean allowsVpnFakeIp(URI uri, boolean vpnCompatibilityEnabled) {
        if (!vpnCompatibilityEnabled || !"https".equalsIgnoreCase(uri.getScheme()) || port(uri) != 443) {
            return false;
        }
        var host = uri.getHost();
        // The JDK also accepts numeric and shortened dotted IPv4 literals.
        return host != null
                && !host.matches("[0-9.]+")
                && !NetUtil.isValidIpV4Address(host)
                && !NetUtil.isValidIpV6Address(host);
    }

    static boolean isVpnFakeAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            return (bytes[0] & 0xff) == 198 && ((bytes[1] & 0xff) == 18 || (bytes[1] & 0xff) == 19);
        }
        // The common Mihomo IPv6 Fake-IP prefix; other unique-local ranges remain rejected.
        return bytes.length == 16
                && (bytes[0] & 0xff) == 0xfd
                && (bytes[1] & 0xff) == 0xfe
                && (bytes[2] & 0xff) == 0xdc
                && (bytes[3] & 0xff) == 0xba
                && (bytes[4] & 0xff) == 0x98
                && (bytes[5] & 0xff) == 0x76
                && bytes[6] == 0
                && bytes[7] == 0;
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            return first != 0
                    && first != 10
                    && first != 127
                    && first < 224
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
        return uri.getPort() != -1 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Bounded response data after the destination and media policy have been checked.
     *
     * @param body decompressed response bytes within the configured limit
     * @param contentType declared media type, or {@code null} when absent
     * @param uri final validated destination, or {@code null} for a synthetic test response
     */
    record Response(byte[] body, MediaType contentType, URI uri) {
        Response(byte[] body, MediaType contentType) {
            this(body, contentType, null);
        }
    }

    /**
     * Internal redirect signal consumed by the bounded redirect-following download path.
     *
     * <p>The URI is revalidated before another request; exception text deliberately omits it because redirect locations
     * can contain signed parameters.
     */
    static final class RedirectException extends RuntimeException {
        private final URI uri;

        RedirectException(URI uri) {
            super("External HTTP response requires a redirect");
            this.uri = uri;
        }

        URI uri() {
            return uri;
        }
    }

    /**
     * A stable, caller-visible rejection produced by local URL, network or response validation.
     *
     * <p>Only messages constructed here may survive outbound HTTP error sanitization.
     */
    private static final class RequestRejectedException extends IllegalArgumentException {
        RequestRejectedException(String message) {
            super(message);
        }
    }

    /**
     * Resolves all addresses for a hostname before they are validated and pinned.
     *
     * <p>The default delegates to {@link InetAddress#getAllByName(String)}. Tests inject deterministic results to
     * exercise mixed public/private DNS answers without relying on external networking.
     */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /**
     * Restricts a connection to the previously validated hostname, port and address set.
     *
     * <p>Resolved IP literals also pass this resolver, preventing a later resolution or pooled connection from silently
     * replacing the checked destination.
     */
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
                protected void doResolve(InetSocketAddress address, Promise<InetSocketAddress> promise) {
                    if (!matches(address)) {
                        promise.setFailure(new IllegalArgumentException("Unexpected HTTP target"));
                        return;
                    }
                    promise.setSuccess(new InetSocketAddress(addresses.getFirst(), port));
                }

                @Override
                protected void doResolveAll(InetSocketAddress address, Promise<List<InetSocketAddress>> promise) {
                    if (!matches(address)) {
                        promise.setFailure(new IllegalArgumentException("Unexpected HTTP target"));
                        return;
                    }
                    promise.setSuccess(addresses.stream()
                            .map(ip -> new InetSocketAddress(ip, port))
                            .toList());
                }

                private boolean matches(InetSocketAddress address) {
                    return address.getPort() == port
                            && (host.equals(address.getHostString()
                                            .replace("[", "")
                                            .replace("]", "")
                                            .toLowerCase(Locale.ROOT))
                                    || address.getAddress() != null && addresses.contains(address.getAddress()));
                }
            };
        }
    }
}
