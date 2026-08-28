package run.halo.aifoundation.provider.siliconflow;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.rerank.StandardRerankResponseDecoder;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankUsage;

/** SiliconFlow native rerank client, including its documented chunk controls. */
public final class SiliconFlowRerankingClient extends AbstractHttpRerankingClient {

    private static final Set<String> OPTIONS = Set.of(
        "return_documents", "max_chunks_per_doc", "overlap_tokens");
    private final String baseUrl;
    private final String modelId;

    public SiliconFlowRerankingClient(String baseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("siliconflow", modelId, apiKey, webClientBuilder, new ResponseDecoder());
        this.baseUrl = ProviderUris.withoutTrailingSlashes(baseUrl);
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(baseUrl + "/rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request,
        Map<String, Object> nativeOptions) {
        validateRequest(request);
        var values = nativeOptions;
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported SiliconFlow rerank option(s): "
                + String.join(", ", unknown));
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        body.put("return_documents", booleanValue(values.get("return_documents"), true));

        var maxChunks = integer(values.get("max_chunks_per_doc"), "max_chunks_per_doc");
        var overlap = integer(values.get("overlap_tokens"), "overlap_tokens");
        if (maxChunks != null && maxChunks < 1) {
            throw new IllegalArgumentException(
                "SiliconFlow max_chunks_per_doc must be positive");
        }
        if (overlap != null && (overlap < 0 || overlap > 80)) {
            throw new IllegalArgumentException(
                "SiliconFlow overlap_tokens must be between 0 and 80");
        }
        putIfPresent(body, "max_chunks_per_doc", maxChunks);
        putIfPresent(body, "overlap_tokens", overlap);
        return body;
    }

    private void validateRequest(RerankRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("SiliconFlow rerank query must not be blank");
        }
        if (request.getQuery() == null) {
            throw new IllegalArgumentException("SiliconFlow rerank query must not be blank");
        }
        if (request.getQuery().isBlank()) {
            throw new IllegalArgumentException("SiliconFlow rerank query must not be blank");
        }
        if (request.getDocuments() == null) {
            throw new IllegalArgumentException("SiliconFlow rerank documents must not be empty");
        }
        if (request.getDocuments().isEmpty()) {
            throw new IllegalArgumentException("SiliconFlow rerank documents must not be empty");
        }
        for (var document : request.getDocuments()) {
            validateDocument(document);
        }
    }

    private void validateDocument(RerankDocument document) {
        if (document == null) {
            throw new IllegalArgumentException(
                "SiliconFlow rerank supports non-blank text documents only");
        }
        if (document.getImage() != null) {
            throw new IllegalArgumentException(
                "SiliconFlow rerank supports non-blank text documents only");
        }
        if (document.getText() == null) {
            throw new IllegalArgumentException(
                "SiliconFlow rerank supports non-blank text documents only");
        }
        if (document.getText().isBlank()) {
            throw new IllegalArgumentException(
                "SiliconFlow rerank supports non-blank text documents only");
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("SiliconFlow return_documents must be boolean");
    }

    private Integer integer(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number && Math.rint(number.doubleValue()) == number.doubleValue()) {
            return number.intValue();
        }
        throw new IllegalArgumentException("SiliconFlow " + field + " must be an integer");
    }

    private static final class ResponseDecoder extends StandardRerankResponseDecoder {

        @Override
        protected RerankUsage usage(Map<String, Object> response) {
            var tokens = mapValue(response.get("tokens"));
            if (tokens == null) {
                return null;
            }
            var input = integerValue(tokens.get("input_tokens"));
            var output = integerValue(tokens.get("output_tokens"));
            if (input == null && output == null) {
                return null;
            }
            return RerankUsage.builder()
                .inputTokens(input)
                .totalTokens(value(input) + value(output))
                .build();
        }

        @Override
        protected Map<String, Object> responseMetadata(Map<String, Object> response) {
            var metadata = new LinkedHashMap<>(super.responseMetadata(response));
            putIfPresent(metadata, "tokens", response.get("tokens"));
            return Map.copyOf(metadata);
        }

        @Override
        protected Map<String, Object> providerMetadata(Map<String, Object> response) {
            var metadata = new LinkedHashMap<>(super.providerMetadata(response));
            putIfPresent(metadata, "tokens", response.get("tokens"));
            return Map.copyOf(metadata);
        }

        private int value(Integer number) {
            return number == null ? 0 : number;
        }
    }

}
