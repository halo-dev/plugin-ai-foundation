package run.halo.aifoundation.provider.gitee;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.provider.support.MediaContentSources;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;

/** Gitee AI text and multimodal reranking adapter. */
public final class GiteeRerankingClient extends AbstractHttpRerankingClient {

    private final String baseUrl;
    private final String modelId;

    public GiteeRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("gitee-moark", modelId, apiKey, webClientBuilder);
        this.baseUrl = ProviderUris.withoutTrailingSlashes(baseUrl);
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        validate(request);
        var path = isMultimodal(request) ? "/rerank/multimodal" : "/rerank";
        return URI.create(baseUrl + path);
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        var body = new LinkedHashMap<String, Object>();
        applyOptions(body, namespacedOptions(request), "model", "query", "documents", "top_n");
        body.put("model", modelId);
        if (isMultimodal(request)) {
            body.put("query", Map.of("text", request.getQuery()));
            body.put("documents", multimodalDocuments(request.getDocuments()));
        } else {
            body.put("query", request.getQuery());
            body.put("documents", documentTexts(request));
        }
        putIfPresent(body, "top_n", topN(request));
        return body;
    }

    @Override
    protected void customizeHeaders(HttpHeaders headers) {
        headers.set(GiteeProvider.FAILOVER_HEADER, "false");
    }

    private void validate(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Gitee AI rerank query must not be blank");
        }
        if (request.getQuery() == null) {
            throw new IllegalArgumentException("Gitee AI rerank query must not be blank");
        }
        if (request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Gitee AI rerank query must not be blank");
        }
        if (request.getDocuments() == null) {
            throw new IllegalArgumentException("Gitee AI rerank documents must not be empty");
        }
        if (request.getDocuments().isEmpty()) {
            throw new IllegalArgumentException("Gitee AI rerank documents must not be empty");
        }
        if (!isMultimodal(request)) {
            validateDocuments(request.getDocuments());
            return;
        }
        if (request.getDocuments().size() > 25) {
            throw new IllegalArgumentException(
                "Gitee AI multimodal rerank accepts at most 25 documents");
        }
        validateDocuments(request.getDocuments());
    }

    private void validateDocuments(List<RerankDocument> documents) {
        for (var document : documents) {
            validateDocument(document);
        }
    }

    private void validateDocument(RerankDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Gitee AI rerank document must not be null");
        }
        if (document.getImage() != null) {
            return;
        }
        if (hasText(document.getText())) {
            return;
        }
        throw new IllegalArgumentException(
            "Gitee AI rerank document must contain text or an image");
    }

    private boolean isMultimodal(RerankRequest request) {
        if (request == null) {
            return false;
        }
        if (request.getDocuments() == null) {
            return false;
        }
        for (var document : request.getDocuments()) {
            if (document == null) {
                continue;
            }
            if (document.getImage() == null) {
                continue;
            }
            return true;
        }
        return false;
    }

    private List<Map<String, Object>> multimodalDocuments(List<RerankDocument> documents) {
        var values = new ArrayList<Map<String, Object>>();
        for (var document : documents) {
            var value = new LinkedHashMap<String, Object>();
            if (hasText(document.getText())) {
                value.put("text", document.getText());
            }
            if (document.getImage() != null) {
                value.put("image", MediaContentSources.urlOrDataUrl(document.getImage(),
                    "Gitee AI rerank image"));
            }
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    private boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }

}
