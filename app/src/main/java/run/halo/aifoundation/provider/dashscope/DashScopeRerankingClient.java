package run.halo.aifoundation.provider.dashscope;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.rerank.RerankResponsePayload;
import run.halo.aifoundation.provider.support.rerank.StandardRerankResponseDecoder;
import run.halo.aifoundation.provider.support.rerank.AbstractHttpRerankingClient;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankUsage;

public final class DashScopeRerankingClient extends AbstractHttpRerankingClient {

    private static final Set<String> COMPATIBLE_OPTIONS = Set.of("instruct");
    private static final Set<String> NATIVE_OPTIONS = Set.of(
        "return_documents", "instruct", "fps");

    private final DashScopeEndpointResolver endpoints;
    private final String modelId;
    private final RequestFormat requestFormat;

    public DashScopeRerankingClient(String configuredBaseUrl, String modelId, String apiKey,
        WebClient.Builder webClientBuilder, RequestFormat requestFormat) {
        super("dashscope", modelId, apiKey, webClientBuilder, new ResponseDecoder());
        this.endpoints = new DashScopeEndpointResolver(configuredBaseUrl);
        this.modelId = modelId;
        this.requestFormat = Objects.requireNonNull(requestFormat,
            "requestFormat must not be null");
    }

    @Override
    protected URI endpoint(RerankRequest request) {
        var url = switch (requestFormat) {
            case COMPATIBLE -> endpoints.compatibleApiBaseUrl() + "/reranks";
            case NATIVE ->
                endpoints.nativeBaseUrl() + "/services/rerank/text-rerank/text-rerank";
        };
        return URI.create(url);
    }

    @Override
    protected Map<String, Object> requestBody(RerankRequest request,
        Map<String, Object> nativeOptions) {
        var options = validatedNativeOptions(nativeOptions);
        return switch (requestFormat) {
            case COMPATIBLE -> compatibleBody(request, options);
            case NATIVE -> nativeBody(request, options);
        };
    }

    private Map<String, Object> compatibleBody(RerankRequest request,
        Map<String, Object> nativeOptions) {
        if (hasImages(request)) {
            throw new IllegalArgumentException(
                "DashScope compatible rerank does not support image documents");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("query", request.getQuery());
        body.put("documents", documentTexts(request));
        putIfPresent(body, "top_n", topN(request));
        putIfPresent(body, "instruct", stringOption(nativeOptions.get("instruct"), "instruct"));
        return body;
    }

    private Map<String, Object> nativeBody(RerankRequest request,
        Map<String, Object> nativeOptions) {
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
        parameters.put("return_documents",
            booleanOption(nativeOptions, "return_documents", true));
        putIfPresent(parameters, "fps", frameRate(nativeOptions.get("fps")));
        putIfPresent(parameters, "instruct",
            stringOption(nativeOptions.get("instruct"), "instruct"));
        var body = new LinkedHashMap<String, Object>();
        body.put("model", modelId);
        body.put("input", input);
        body.put("parameters", parameters);
        return body;
    }

    private Map<String, Object> validatedNativeOptions(Map<String, Object> nativeOptions) {
        var values = nativeOptions != null ? nativeOptions : Map.<String, Object>of();
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(supportedNativeOptions());
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported DashScope rerank option(s): "
                + String.join(", ", unknown));
        }
        return values;
    }

    private Set<String> supportedNativeOptions() {
        return switch (requestFormat) {
            case COMPATIBLE -> COMPATIBLE_OPTIONS;
            case NATIVE -> NATIVE_OPTIONS;
        };
    }

    private boolean booleanOption(Map<String, Object> values, String field, boolean fallback) {
        var value = values.get(field);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean result) {
            return result;
        }
        throw new IllegalArgumentException("DashScope rerank " + field + " must be boolean");
    }

    private String stringOption(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("DashScope rerank " + field
                + " must be a non-blank string");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("DashScope rerank " + field
                + " must be a non-blank string");
        }
        return text;
    }

    private Number frameRate(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw invalidFrameRate();
        }
        var numericValue = number.doubleValue();
        if (!Double.isFinite(numericValue)) {
            throw invalidFrameRate();
        }
        if (numericValue < 0.0) {
            throw invalidFrameRate();
        }
        if (numericValue > 1.0) {
            throw invalidFrameRate();
        }
        return number;
    }

    private IllegalArgumentException invalidFrameRate() {
        return new IllegalArgumentException(
            "DashScope native rerank fps must be between 0 and 1");
    }

    private boolean hasImages(RerankRequest request) {
        return request.getDocuments().stream().anyMatch(document -> document.getImage() != null);
    }

    private Map<String, Object> document(run.halo.aifoundation.rerank.RerankDocument document) {
        var result = new LinkedHashMap<String, Object>();
        putText(result, document.getText());
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

    private void putText(Map<String, Object> target, String text) {
        if (text == null) {
            return;
        }
        if (text.isBlank()) {
            return;
        }
        target.put("text", text);
    }

    public enum RequestFormat {
        COMPATIBLE,
        NATIVE
    }

    private static final class ResponseDecoder extends StandardRerankResponseDecoder {

        @Override
        public RerankResponsePayload decode(Map<String, Object> response, String requestedModel) {
            var output = mapValue(response.get("output"));
            var results = output == null
                ? listValue(response.get("results"))
                : listValue(output.get("results"));
            if (results == null) {
                results = List.of();
            }
            var responseId = stringValue(response.get("request_id"));
            if (responseId == null) {
                responseId = stringValue(response.get("id"));
            }
            return payload(response, requestedModel, responseId, results, usage(response));
        }

        @Override
        protected RerankUsage usage(Map<String, Object> response) {
            var usage = mapValue(response.get("usage"));
            if (usage == null) {
                return null;
            }
            var inputTokens = integerValue(usage.get("prompt_tokens"));
            if (inputTokens == null) {
                inputTokens = integerValue(usage.get("input_tokens"));
            }
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
            putIfPresent(metadata, "id", response.get("id"));
            putIfPresent(metadata, "model", response.get("model"));
            putIfPresent(metadata, "object", response.get("object"));
            putIfPresent(metadata, "usage", response.get("usage"));
            if (provider) {
                putIfPresent(metadata, "output", response.get("output"));
            }
            return Map.copyOf(metadata);
        }
    }
}
