package run.halo.aifoundation.provider.aihubmix;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;

/** AIHubMix native text reranking client. */
public final class AiHubMixRerankingClient extends AbstractHttpRerankingClient {

    private static final Set<String> OPTIONS = Set.of("return_documents");

    private final String baseUrl;
    private final String modelId;
    private final Map<String, String> headers;

    public AiHubMixRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder, Map<String, String> headers) {
        super("aihubmix", modelId, apiKey, webClientBuilder);
        this.baseUrl = ProviderUris.withoutTrailingSlashes(baseUrl);
        this.modelId = modelId;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(baseUrl + "/rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("AIHubMix rerank query must not be blank");
        }
        if (request.getQuery() == null) {
            throw new IllegalArgumentException("AIHubMix rerank query must not be blank");
        }
        if (request.getQuery().isBlank()) {
            throw new IllegalArgumentException("AIHubMix rerank query must not be blank");
        }
        if (request.getDocuments() == null) {
            throw new IllegalArgumentException(
                "AIHubMix rerank requires non-blank text documents");
        }
        if (request.getDocuments().isEmpty()) {
            throw new IllegalArgumentException(
                "AIHubMix rerank requires non-blank text documents");
        }
        for (var document : request.getDocuments()) {
            validateDocument(document);
        }
        validateTopN(request);
        var values = namespacedOptions(request);
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported AIHubMix rerank option(s): "
                + String.join(", ", unknown));
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        putIfPresent(body, "return_documents",
            booleanValue(values.get("return_documents")));
        return body;
    }

    private void validateDocument(RerankDocument document) {
        if (document == null) {
            throw new IllegalArgumentException(
                "AIHubMix rerank requires non-blank text documents");
        }
        if (document.getImage() != null) {
            throw new IllegalArgumentException(
                "AIHubMix rerank requires non-blank text documents");
        }
        if (document.getText() == null) {
            throw new IllegalArgumentException(
                "AIHubMix rerank requires non-blank text documents");
        }
        if (document.getText().isBlank()) {
            throw new IllegalArgumentException(
                "AIHubMix rerank requires non-blank text documents");
        }
    }

    private void validateTopN(RerankRequest request) {
        if (request.getTopN() == null) {
            return;
        }
        if (request.getTopN() < 1) {
            throw new IllegalArgumentException(
                "AIHubMix rerank top_n must be between 1 and the document count");
        }
        if (request.getTopN() > request.getDocuments().size()) {
            throw new IllegalArgumentException(
                "AIHubMix rerank top_n must be between 1 and the document count");
        }
    }

    @Override
    protected void customizeHeaders(HttpHeaders headers) {
        this.headers.forEach(headers::set);
    }

    private Boolean booleanValue(Object value) {
        if (value == null || value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalArgumentException("AIHubMix return_documents must be boolean");
    }

}
