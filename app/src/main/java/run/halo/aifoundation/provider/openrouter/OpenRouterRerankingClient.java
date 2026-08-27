package run.halo.aifoundation.provider.openrouter;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.MediaContentSources;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;

/** OpenRouter rerank router with native text/image documents and provider routing. */
public final class OpenRouterRerankingClient extends AbstractHttpRerankingClient {

    private static final Set<String> OPTIONS = Set.of("provider");

    private final String baseUrl;
    private final String modelId;
    private final Map<String, String> headers;

    public OpenRouterRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder, Map<String, String> headers) {
        super("openrouter", modelId, apiKey, webClientBuilder);
        this.baseUrl = ProviderUris.withoutTrailingSlashes(baseUrl);
        this.modelId = modelId;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(baseUrl + "/rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OpenRouter rerank documents must not be empty");
        }
        if (request.getDocuments() == null) {
            throw new IllegalArgumentException("OpenRouter rerank documents must not be empty");
        }
        if (request.getDocuments().isEmpty()) {
            throw new IllegalArgumentException("OpenRouter rerank documents must not be empty");
        }
        var values = namespacedOptions(request);
        var unknown = new java.util.LinkedHashSet<>(values.keySet());
        unknown.removeAll(OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported OpenRouter rerank option(s): "
                + String.join(", ", unknown));
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", request.getDocuments().stream().map(this::document).toList());
        putIfPresent(body, "top_n", topN(request));
        if (values.get("provider") != null) {
            OpenRouterRoutingOptions.validate(values.get("provider"), "rerank");
            body.put("provider", values.get("provider"));
        }
        return body;
    }

    @Override
    protected void customizeHeaders(HttpHeaders headers) {
        this.headers.forEach(headers::set);
    }

    private Object document(RerankDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("OpenRouter rerank documents must not contain null");
        }
        var text = document.getText();
        var image = document.getImage();
        if (image == null) {
            if (!hasText(text)) {
                throw new IllegalArgumentException(
                    "OpenRouter rerank document must contain text or an image");
            }
            return text;
        }
        var value = new LinkedHashMap<String, Object>();
        if (hasText(text)) {
            value.put("text", text);
        }
        value.put("image", MediaContentSources.urlOrDataUrl(image,
            "OpenRouter rerank image"));
        return Map.copyOf(value);
    }

    private boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }
}
