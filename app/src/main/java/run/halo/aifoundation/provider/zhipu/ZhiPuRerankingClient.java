package run.halo.aifoundation.provider.zhipu;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.rerank.StandardRerankResponseDecoder;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;

/** Native BigModel rerank client. */
public final class ZhiPuRerankingClient extends AbstractHttpRerankingClient {

    private static final Set<String> OPTIONS = Set.of(
        "return_documents", "return_raw_scores", "request_id", "user_id");

    private final String baseUrl;
    private final String modelId;

    public ZhiPuRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("zhipuai", modelId, apiKey, webClientBuilder, new ResponseDecoder());
        this.baseUrl = ProviderUris.withoutTrailingSlashes(baseUrl);
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(baseUrl + "/rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        validateRequest(request);
        var values = namespacedOptions(request);
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Zhipu rerank option(s): "
                + String.join(", ", unknown));
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        putIfPresent(body, "return_documents",
            booleanValue(values.get("return_documents"), "return_documents"));
        putIfPresent(body, "return_raw_scores",
            booleanValue(values.get("return_raw_scores"), "return_raw_scores"));
        putIdentifier(body, values, "request_id", 6, 64);
        putIdentifier(body, values, "user_id", 1, Integer.MAX_VALUE);
        return body;
    }

    private void validateRequest(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                "Zhipu rerank query must contain 1 to 4096 characters");
        }
        validateQuery(request.getQuery());
        validateDocuments(request.getDocuments());
        validateTopN(request);
    }

    private void validateQuery(String query) {
        if (query == null) {
            throw new IllegalArgumentException(
                "Zhipu rerank query must contain 1 to 4096 characters");
        }
        if (query.isBlank()) {
            throw new IllegalArgumentException(
                "Zhipu rerank query must contain 1 to 4096 characters");
        }
        if (query.length() > 4096) {
            throw new IllegalArgumentException(
                "Zhipu rerank query must contain 1 to 4096 characters");
        }
    }

    private void validateDocuments(List<RerankDocument> documents) {
        if (documents == null) {
            throw new IllegalArgumentException(
                "Zhipu rerank requires between 1 and 128 documents");
        }
        if (documents.isEmpty()) {
            throw new IllegalArgumentException(
                "Zhipu rerank requires between 1 and 128 documents");
        }
        if (documents.size() > 128) {
            throw new IllegalArgumentException(
                "Zhipu rerank requires between 1 and 128 documents");
        }
        for (var document : documents) {
            validateDocument(document);
        }
    }

    private void validateDocument(RerankDocument document) {
        if (document == null) {
            throw new IllegalArgumentException(
                "Zhipu rerank supports text documents of 1 to 4096 characters only");
        }
        if (document.getImage() != null) {
            throw new IllegalArgumentException(
                "Zhipu rerank supports text documents of 1 to 4096 characters only");
        }
        var text = document.getText();
        if (text == null) {
            throw new IllegalArgumentException(
                "Zhipu rerank supports text documents of 1 to 4096 characters only");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                "Zhipu rerank supports text documents of 1 to 4096 characters only");
        }
        if (text.length() > 4096) {
            throw new IllegalArgumentException(
                "Zhipu rerank supports text documents of 1 to 4096 characters only");
        }
    }

    private void validateTopN(RerankRequest request) {
        if (request.getTopN() == null) {
            return;
        }
        if (request.getTopN() < 0) {
            throw new IllegalArgumentException(
                "Zhipu rerank top_n must be between 0 and the document count");
        }
        if (request.getTopN() > request.getDocuments().size()) {
            throw new IllegalArgumentException(
                "Zhipu rerank top_n must be between 0 and the document count");
        }
    }

    private Boolean booleanValue(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Zhipu rerank " + field + " must be boolean");
    }

    private void putIdentifier(Map<String, Object> body, Map<String, Object> values,
        String field, int min, int max) {
        var value = values.get(field);
        if (value == null) {
            return;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                "Zhipu rerank " + field + " has an invalid length");
        }
        if (text.length() < min) {
            throw new IllegalArgumentException(
                "Zhipu rerank " + field + " has an invalid length");
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(
                "Zhipu rerank " + field + " has an invalid length");
        }
        body.put(field, value);
    }

    private static final class ResponseDecoder extends StandardRerankResponseDecoder {

        @Override
        protected Map<String, Object> responseMetadata(Map<String, Object> response) {
            return metadata(response, super.responseMetadata(response));
        }

        @Override
        protected Map<String, Object> providerMetadata(Map<String, Object> response) {
            return metadata(response, super.providerMetadata(response));
        }

        private Map<String, Object> metadata(Map<String, Object> response,
            Map<String, Object> base) {
            var metadata = new LinkedHashMap<>(base);
            putIfPresent(metadata, "requestId", response.get("request_id"));
            return Map.copyOf(metadata);
        }
    }

}
