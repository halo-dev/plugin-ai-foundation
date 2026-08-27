package run.halo.aifoundation.provider.support.rerank;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankResponseMetadata;
import run.halo.aifoundation.rerank.RerankResult;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

public abstract class AbstractHttpRerankingClient implements ProviderRerankingClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final String providerType;
    private final String modelId;
    private final String apiKey;
    private final WebClient webClient;
    private final RerankResponseDecoder responseDecoder;

    protected AbstractHttpRerankingClient(String providerType, String modelId, String apiKey,
        WebClient.Builder webClientBuilder) {
        this(providerType, modelId, apiKey, webClientBuilder,
            new StandardRerankResponseDecoder());
    }

    protected AbstractHttpRerankingClient(String providerType, String modelId, String apiKey,
        WebClient.Builder webClientBuilder, RerankResponseDecoder responseDecoder) {
        this.providerType = providerType;
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.webClient = webClientBuilder.build();
        this.responseDecoder = java.util.Objects.requireNonNull(responseDecoder,
            "responseDecoder must not be null");
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request) {
        return rerank(request, null);
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request, ParameterMappingTarget target) {
        var uri = endpoint(request);
        var body = requestBody(request);
        applyMappedParameters(body, target);
        var diagnostics = ProviderDiagnostics.create(providerType, "rerank");
        diagnostics.request(uri.toString(), body, false);
        return webClient.post()
            .uri(uri)
            .headers(headers -> {
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.setBearerAuth(apiKey);
                }
                customizeHeaders(headers);
                if (request.getHeaders() != null) {
                    request.getHeaders().forEach(headers::set);
                }
            })
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response, providerType,
                        "rerank", diagnostics);
                }
                diagnostics.responseStatus(response.statusCode().value());
                return response.bodyToMono(MAP_TYPE)
                    .map(json -> response(json, request, uri));
            });
    }

    @SuppressWarnings("unchecked")
    private void applyMappedParameters(Map<String, Object> body, ParameterMappingTarget target) {
        if (target == null) {
            return;
        }
        body.remove("top_n");
        var parameters = body.get("parameters") instanceof Map<?, ?> values
            ? (Map<String, Object>) values : null;
        if (parameters != null) {
            parameters.remove("top_n");
        }
        body.putAll(target.root());
        if (!target.parameters().isEmpty()) {
            if (parameters == null) {
                parameters = new LinkedHashMap<>();
                body.put("parameters", parameters);
            }
            parameters.putAll(target.parameters());
        }
    }

    protected abstract URI endpoint(RerankRequest request);

    protected abstract Map<String, Object> requestBody(RerankRequest request);

    protected void customizeHeaders(HttpHeaders headers) {
    }

    protected Map<String, Object> namespacedOptions(RerankRequest request) {
        if (request == null) {
            return Map.of();
        }
        var options = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), providerType);
        return Map.copyOf(options);
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
        var payload = responseDecoder.decode(root, modelId);
        var results = new ArrayList<RerankResult>();
        for (var item : payload.results()) {
            if (item instanceof Map<?, ?> node) {
                results.add(result(node, request));
            }
        }
        var metadata = new LinkedHashMap<>(payload.providerMetadata());
        metadata.put("providerType", providerType);
        metadata.put("endpoint", uri.toString());
        return RerankResponse.builder()
            .query(request.getQuery())
            .results(List.copyOf(results))
            .usage(payload.usage())
            .response(RerankResponseMetadata.builder()
                .id(payload.id())
                .model(payload.model())
                .metadata(payload.responseMetadata())
                .build())
            .providerMetadata(Map.copyOf(metadata))
            .build();
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
        if (isDocumentIndex(index, request)) {
            return request.getDocuments().get(index);
        }
        var value = node.get("document");
        if (value instanceof Map<?, ?> map) {
            return RerankDocument.of(stringValue(map.get("text")));
        }
        return RerankDocument.of(stringValue(value));
    }

    private boolean isDocumentIndex(Integer index, RerankRequest request) {
        if (index == null) {
            return false;
        }
        if (index < 0) {
            return false;
        }
        return index < request.getDocuments().size();
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

}
