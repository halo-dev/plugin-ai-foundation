package run.halo.aifoundation.provider.dashscope;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.RerankResponsePayload;
import run.halo.aifoundation.provider.support.rerank.StandardRerankResponseDecoder;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankUsage;

public final class DashScopeRerankingClient extends AbstractHttpRerankingClient {

    private final DashScopeEndpointResolver endpoints;
    private final String modelId;

    public DashScopeRerankingClient(String configuredBaseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        super("dashscope", modelId, apiKey, webClientBuilder, new ResponseDecoder());
        this.endpoints = new DashScopeEndpointResolver(configuredBaseUrl);
        this.modelId = modelId;
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        return URI.create(endpoints.nativeBaseUrl() + "/services/rerank/text-rerank/text-rerank");
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request) {
        return nativeBody(request);
    }

    private Map<String, Object> nativeBody(RerankRequest request) {
        var input = new LinkedHashMap<String, Object>();
        if (hasImages(request)) {
            input.put("query", Map.of("text", request.getQuery()));
            input.put("documents", request.getDocuments().stream().map(this::document).toList());
        } else {
            input.put("query", request.getQuery());
            input.put("documents", documentTexts(request));
        }
        var parameters = new LinkedHashMap<String, Object>();
        putIfPresent(parameters, "top_n", topN(request));
        parameters.put("return_documents", true);
        return Map.of(
            "model", modelId,
            "input", input,
            "parameters", parameters
        );
    }

    private boolean hasImages(RerankRequest request) {
        return request.getDocuments().stream().anyMatch(document -> document.getImage() != null);
    }

    private Map<String, Object> document(run.halo.aifoundation.rerank.RerankDocument document) {
        var result = new LinkedHashMap<String, Object>();
        if (document.getText() != null && !document.getText().isBlank()) {
            result.put("text", document.getText());
        }
        var image = document.getImage();
        if (image != null && image.isUrl()) {
            result.put("image", image.getUrl());
        } else if (image != null && image.isData()) {
            result.put("image", "data:" + image.getMediaType() + ";base64," + image.getData());
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                "DashScope rerank documents must contain text or an image");
        }
        return Map.copyOf(result);
    }

    private static final class ResponseDecoder extends StandardRerankResponseDecoder {

        @Override
        public RerankResponsePayload decode(Map<String, Object> response, String requestedModel) {
            var output = mapValue(response.get("output"));
            var results = output == null ? List.of() : listValue(output.get("results"));
            if (results == null) {
                results = List.of();
            }
            return payload(response, requestedModel, stringValue(response.get("request_id")),
                results, usage(response));
        }

        @Override
        protected RerankUsage usage(Map<String, Object> response) {
            var usage = mapValue(response.get("usage"));
            if (usage == null) {
                return null;
            }
            var inputTokens = integerValue(usage.get("input_tokens"));
            var totalTokens = integerValue(usage.get("total_tokens"));
            if (inputTokens == null && totalTokens == null) {
                return null;
            }
            return RerankUsage.builder()
                .inputTokens(inputTokens)
                .totalTokens(totalTokens)
                .build();
        }

        @Override
        protected Map<String, Object> responseMetadata(Map<String, Object> response) {
            return metadata(response, false);
        }

        @Override
        protected Map<String, Object> providerMetadata(Map<String, Object> response) {
            return metadata(response, true);
        }

        private Map<String, Object> metadata(Map<String, Object> response, boolean provider) {
            var metadata = new LinkedHashMap<String, Object>();
            putIfPresent(metadata, "requestId", response.get("request_id"));
            putIfPresent(metadata, "usage", response.get("usage"));
            if (provider) {
                putIfPresent(metadata, "output", response.get("output"));
            }
            return Map.copyOf(metadata);
        }
    }
}
