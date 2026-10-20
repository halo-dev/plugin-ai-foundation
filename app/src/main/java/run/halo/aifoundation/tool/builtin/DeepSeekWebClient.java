package run.halo.aifoundation.tool.builtin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Calls DeepSeek's authenticated Search and Fetch APIs using a tool-specific API key.
 *
 * <p>Requests always use DeepSeek's official API endpoint. A page URL is sent only as JSON to the Fetch API; this
 * client never sends the DeepSeek credential to that page or follows an API redirect. Responses are bounded and
 * validated before being returned to a tool.
 *
 * <pre>{@code
 * client.search(options, Map.of("query", "Halo CMS", "count", 5));
 * client.fetch(options, Map.of("url", "https://www.halo.run/"));
 * }</pre>
 */
@Component
public class DeepSeekWebClient {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() {};
    private final WebClient client;

    /** Creates the fixed-endpoint client without redirects or automatic request retries. */
    public DeepSeekWebClient() {
        this(WebClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().followRedirect(false).disableRetry(true)))
                .build());
    }

    /**
     * Supplies an isolated HTTP client for local transport tests.
     *
     * @param client test transport; no tool parameter or saved setting can supply this client
     */
    DeepSeekWebClient(WebClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /**
     * Searches DeepSeek's web index and preserves its structured result metadata.
     *
     * @param options current Secret credential and response limits
     * @param validatedBody validated Search API fields, including a nonblank {@code query}
     * @return the JSON response, whose {@code results} may be empty and contain nullable metadata
     */
    public Mono<Map<String, Object>> search(
            DeepSeekWebToolSettings.Options options, Map<String, Object> validatedBody) {
        return request(options, "search", validatedBody).map(DeepSeekWebClient::validateSearch);
    }

    /**
     * Fetches a page through DeepSeek and returns the page title and Markdown content.
     *
     * @param options current Secret credential and response limits
     * @param validatedBody a validated HTTP or HTTPS page {@code url}
     * @return the JSON response with a possibly empty title and nonblank Markdown content
     */
    public Mono<Map<String, Object>> fetch(DeepSeekWebToolSettings.Options options, Map<String, Object> validatedBody) {
        return request(options, "fetch", validatedBody).map(DeepSeekWebClient::validateFetch);
    }

    private Mono<Map<String, Object>> request(
            DeepSeekWebToolSettings.Options options, String operation, Map<String, Object> body) {
        return Mono.defer(() -> {
                    validateOptions(options);
                    var requestClient = client.mutate()
                            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(options.maxResponseBytes()))
                            .build();
                    return requestClient
                            .post()
                            .uri("/v1/tools/" + operation)
                            .headers(headers -> headers.setBearerAuth(options.apiKey()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .exchangeToMono(response -> {
                                if (response.statusCode().value() != 200) {
                                    return response.releaseBody()
                                            .then(Mono.error(statusFailure(
                                                    response.statusCode().value())));
                                }
                                return response.bodyToMono(byte[].class)
                                        .switchIfEmpty(Mono.error(failure("DeepSeek returned an empty JSON response")))
                                        .map(DeepSeekWebClient::parse);
                            })
                            .timeout(options.timeout());
                })
                .onErrorMap(DeepSeekWebClient::safeFailure);
    }

    private static void validateOptions(DeepSeekWebToolSettings.Options options) {
        if (options == null || options.apiKey() == null || options.apiKey().isBlank()) {
            throw failure("Configure the DeepSeek API Key in this tool's configuration before using it");
        }
        if (options.maxResponseBytes() <= 0) {
            throw failure("DeepSeek response size limit must be positive");
        }
        if (options.timeout() == null
                || options.timeout().isNegative()
                || options.timeout().isZero()) {
            throw failure("DeepSeek request timeout must be positive");
        }
    }

    private static Map<String, Object> parse(byte[] bytes) {
        try {
            var result = JSON.readValue(bytes, RESPONSE_TYPE);
            if (result == null) {
                throw failure("DeepSeek returned an invalid JSON response");
            }
            return result;
        } catch (IOException error) {
            throw failure("DeepSeek returned an invalid JSON response");
        }
    }

    private static Map<String, Object> validateSearch(Map<String, Object> response) {
        if (!(response.get("results") instanceof List<?> results)) {
            throw failure("DeepSeek Search response is missing a valid results array");
        }
        for (var result : results) {
            if (!(result instanceof Map<?, ?> fields)) {
                throw failure("DeepSeek Search returned an invalid result");
            }
            validateSearchResult(fields);
        }
        return response;
    }

    private static void validateSearchResult(Map<?, ?> fields) {
        for (var field : List.of("title", "url", "snippet", "summary", "content")) {
            if (!(fields.get(field) instanceof String)) {
                throw failure("DeepSeek Search returned a result with an invalid " + field + " field");
            }
        }
        var url = (String) fields.get("url");
        if (!validResultUrl(url)) {
            throw failure("DeepSeek Search returned a result with an invalid url field");
        }
        for (var field : List.of("site_name", "site_icon", "date")) {
            var value = fields.get(field);
            if (value != null && !(value instanceof String)) {
                throw failure("DeepSeek Search returned a result with an invalid " + field + " field");
            }
        }
        if (!validScore(fields.get("rerank_score"))) {
            throw failure("DeepSeek Search returned a result with an invalid rerank_score field");
        }
        var authority = fields.get("authority");
        if (!(authority instanceof Integer number) || number < -1 || number > 4) {
            throw failure("DeepSeek Search returned a result with an invalid authority field");
        }
    }

    private static boolean validScore(Object value) {
        if (!(value instanceof Number score)) {
            return false;
        }
        var number = score.doubleValue();
        return Double.isFinite(number) && number >= 0 && number <= 3;
    }

    private static boolean validResultUrl(String value) {
        try {
            var uri = URI.create(value);
            return webScheme(uri) && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean webScheme(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
    }

    private static Map<String, Object> validateFetch(Map<String, Object> response) {
        if (!(response.get("title") instanceof String)) {
            throw failure("DeepSeek Fetch response is missing a valid title field");
        }
        if (!(response.get("content") instanceof String content) || content.isBlank()) {
            throw failure("DeepSeek Fetch did not return readable webpage content");
        }
        return response;
    }

    private static DeepSeekWebException statusFailure(int status) {
        var explanation =
                switch (status) {
                    case 400 -> "request rejected; check the tool arguments";
                    case 401 -> "API Key is missing or invalid";
                    case 402 -> "account balance is insufficient";
                    case 403 -> "request blocked by the service's security policy";
                    case 404 -> "no readable webpage content or unsupported API endpoint";
                    case 408, 504 -> "upstream request timed out";
                    case 413 -> "request exceeds the service's size limit";
                    case 429 -> "request rate limit exceeded; try again later";
                    default ->
                        status >= 300 && status < 400
                                ? "redirects are not allowed"
                                : "upstream service is unavailable; try again later";
                };
        return failure("DeepSeek request failed (HTTP " + status + "): " + explanation);
    }

    private static Throwable safeFailure(Throwable error) {
        if (error instanceof DeepSeekWebException) {
            return error;
        }
        if (hasCause(error, TimeoutException.class)) {
            return failure("DeepSeek request timed out; try again later or increase the tool timeout");
        }
        if (hasCause(error, DataBufferLimitException.class)) {
            return failure("DeepSeek response exceeds the configured response size limit");
        }
        return failure("Could not connect to the DeepSeek API; check the network and try again later");
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        var current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static DeepSeekWebException failure(String message) {
        return new DeepSeekWebException(message);
    }

    /** Carries only safe, locally generated diagnostics without upstream response bodies or causes. */
    private static final class DeepSeekWebException extends IllegalStateException {
        private DeepSeekWebException(String message) {
            super(message);
        }
    }
}
