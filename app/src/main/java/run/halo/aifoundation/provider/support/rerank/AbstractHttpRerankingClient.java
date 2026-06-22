package run.halo.aifoundation.provider.support.rerank;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankResponseMetadata;
import run.halo.aifoundation.rerank.RerankResult;
import run.halo.aifoundation.rerank.RerankUsage;

abstract class AbstractHttpRerankingClient implements ProviderRerankingClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final String providerType;
    private final String modelId;
    private final String apiKey;
    private final WebClient webClient;

    AbstractHttpRerankingClient(String providerType, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        this.providerType = providerType;
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request) {
        var uri = endpoint(request);
        var body = requestBody(request);
        return webClient.post()
            .uri(uri)
            .headers(headers -> {
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.setBearerAuth(apiKey);
                }
            })
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return errorMono(response);
                }
                return response.bodyToMono(MAP_TYPE)
                    .map(json -> response(json, request, uri));
            });
    }

    protected abstract URI endpoint(RerankRequest request);

    protected abstract Map<String, Object> requestBody(RerankRequest request);

    protected List<?> resultNodes(Map<String, Object> root) {
        var topLevel = listValue(root.get("results"));
        if (topLevel != null) {
            return topLevel;
        }
        var output = mapValue(root.get("output"));
        return output != null ? listValue(output.get("results")) : List.of();
    }

    protected String responseId(Map<String, Object> root) {
        var id = stringValue(root.get("id"));
        return id != null ? id : stringValue(root.get("request_id"));
    }

    protected String responseModel(Map<String, Object> root) {
        var model = stringValue(root.get("model"));
        return model != null ? model : modelId;
    }

    protected RerankUsage usage(Map<String, Object> root) {
        var usage = mapValue(root.get("usage"));
        var promptTokens = integerValue(usage != null ? usage.get("prompt_tokens") : null);
        var totalTokens = integerValue(usage != null ? usage.get("total_tokens") : null);
        if (promptTokens == null) {
            promptTokens = integerValue(usage != null ? usage.get("input_tokens") : null);
        }
        if (promptTokens == null || totalTokens == null) {
            var meta = mapValue(root.get("meta"));
            var tokens = mapValue(meta != null ? meta.get("tokens") : null);
            if (promptTokens == null) {
                promptTokens = integerValue(tokens != null ? tokens.get("input_tokens") : null);
            }
            if (totalTokens == null && tokens != null) {
                var outputTokens = integerValue(tokens.get("output_tokens"));
                if (promptTokens != null || outputTokens != null) {
                    totalTokens = safe(promptTokens) + safe(outputTokens);
                }
            }
        }
        if (promptTokens == null && totalTokens == null) {
            return null;
        }
        return RerankUsage.builder()
            .inputTokens(promptTokens)
            .totalTokens(totalTokens)
            .build();
    }

    protected Map<String, Object> namespacedOptions(RerankRequest request) {
        if (request == null || request.getProviderOptions() == null) {
            return Map.of();
        }
        var options = request.getProviderOptions().get(providerType);
        return options != null ? options : Map.of();
    }

    protected List<String> documentTexts(RerankRequest request) {
        return request.getDocuments().stream()
            .map(RerankDocument::getText)
            .toList();
    }

    protected void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    protected void applyOptions(Map<String, Object> target, Map<String, Object> options,
        String... ignored) {
        if (options == null || options.isEmpty()) {
            return;
        }
        var ignoredKeys = List.of(ignored);
        for (var entry : options.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null
                && !ignoredKeys.contains(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    protected Integer topN(RerankRequest request) {
        return request.getTopN();
    }

    private RerankResponse response(Map<String, Object> root, RerankRequest request, URI uri) {
        var results = new ArrayList<RerankResult>();
        for (var item : resultNodes(root)) {
            if (item instanceof Map<?, ?> node) {
                results.add(result(node, request));
            }
        }
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("providerType", providerType);
        metadata.put("endpoint", uri.toString());
        putIfPresent(metadata, "requestId", stringValue(root.get("request_id")));
        putIfPresent(metadata, "object", stringValue(root.get("object")));
        putIfPresent(metadata, "rawMeta", root.get("meta"));
        return RerankResponse.builder()
            .query(request.getQuery())
            .results(List.copyOf(results))
            .usage(usage(root))
            .response(RerankResponseMetadata.builder()
                .id(responseId(root))
                .model(responseModel(root))
                .metadata(responseMetadata(root))
                .build())
            .providerMetadata(Map.copyOf(metadata))
            .build();
    }

    private Map<String, Object> responseMetadata(Map<String, Object> root) {
        var metadata = new LinkedHashMap<String, Object>();
        putIfPresent(metadata, "created", root.get("created"));
        putIfPresent(metadata, "requestId", root.get("request_id"));
        putIfPresent(metadata, "object", root.get("object"));
        putIfPresent(metadata, "usage", root.get("usage"));
        putIfPresent(metadata, "meta", root.get("meta"));
        return Map.copyOf(metadata);
    }

    private RerankResult result(Map<?, ?> node, RerankRequest request) {
        var index = integerValue(node.get("index"));
        var document = document(node, request, index);
        var providerMetadata = new LinkedHashMap<String, Object>();
        putIfPresent(providerMetadata, "document", node.get("document"));
        putIfPresent(providerMetadata, "rawScore", node.get("relevance_score"));
        return RerankResult.builder()
            .index(index != null ? index : -1)
            .document(document)
            .score(doubleValue(node.get("relevance_score")))
            .providerMetadata(Map.copyOf(providerMetadata))
            .build();
    }

    private RerankDocument document(Map<?, ?> node, RerankRequest request, Integer index) {
        if (index != null && index >= 0 && index < request.getDocuments().size()) {
            return request.getDocuments().get(index);
        }
        var value = node.get("document");
        if (value instanceof Map<?, ?> map) {
            return RerankDocument.of(stringValue(map.get("text")));
        }
        return RerankDocument.of(stringValue(value));
    }

    private <T> Mono<T> errorMono(ClientResponse response) {
        return response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> Mono.error(new IllegalStateException(
                providerType + " rerank request failed: status="
                    + response.statusCode().value() + ", body=" + body)));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    protected List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    protected String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    protected Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    protected Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }

    private int safe(Integer value) {
        return value != null ? value : 0;
    }
}
