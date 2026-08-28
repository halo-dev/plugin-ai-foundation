package run.halo.aifoundation.provider.ernie;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.rerank.RerankRequest;

/** Qianfan v2 reranking adapter with the provider's documented request limits. */
public final class ErnieRerankingClient extends AbstractHttpRerankingClient {

    private static final int MAX_DOCUMENTS = 64;
    private static final int MAX_QUERY_CHARACTERS = 1600;
    private static final int MAX_DOCUMENT_CHARACTERS = 4096;

    private final String baseUrl;
    private final String modelId;

    public ErnieRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("ernie", modelId, apiKey, webClientBuilder);
        this.baseUrl = ProviderUris.withoutTrailingSlashes(baseUrl);
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        validate(request);
        return URI.create(baseUrl + "/rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request,
        Map<String, Object> nativeOptions) {
        var body = new LinkedHashMap<String, Object>();
        applyOptions(body, nativeOptions, "model", "query", "documents", "top_n");
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        return body;
    }

    private void validate(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Qianfan rerank query must not be blank");
        }
        if (request.getQuery() == null) {
            throw new IllegalArgumentException("Qianfan rerank query must not be blank");
        }
        if (request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Qianfan rerank query must not be blank");
        }
        if (request.getQuery().length() > MAX_QUERY_CHARACTERS) {
            throw new IllegalArgumentException(
                "Qianfan rerank query must not exceed 1600 characters");
        }
        if (request.getDocuments() == null) {
            throw new IllegalArgumentException("Qianfan rerank documents must not be empty");
        }
        if (request.getDocuments().isEmpty()) {
            throw new IllegalArgumentException("Qianfan rerank documents must not be empty");
        }
        if (request.getDocuments().size() > MAX_DOCUMENTS) {
            throw new IllegalArgumentException(
                "Qianfan rerank accepts at most 64 documents per request");
        }
        for (var document : request.getDocuments()) {
            if (document == null) {
                throw new IllegalArgumentException("Qianfan rerank document must not be blank");
            }
            var text = document.getText();
            if (text == null) {
                throw new IllegalArgumentException("Qianfan rerank document must not be blank");
            }
            if (text.isBlank()) {
                throw new IllegalArgumentException("Qianfan rerank document must not be blank");
            }
            if (text.length() > MAX_DOCUMENT_CHARACTERS) {
                throw new IllegalArgumentException(
                    "Qianfan rerank document must not exceed 4096 characters");
            }
        }
    }

}
