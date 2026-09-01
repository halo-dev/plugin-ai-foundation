package run.halo.aifoundation.provider.dashscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;

/** DashScope-native text embedding adapter with dense/sparse metadata preservation. */
public final class DashScopeEmbeddingModel
    implements EmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMBEDDING_PATH =
        "/services/embeddings/text-embedding/text-embedding";

    private final DashScopeEmbeddingOptions defaultOptions;
    private final EmbeddingHttpTransport transport;

    public DashScopeEmbeddingModel(DashScopeEmbeddingOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaultOptions = defaultOptions;
        this.transport = new EmbeddingHttpTransport("dashscope", webClientBuilder);
    }

    public DashScopeEmbeddingOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        return call(request, Map.of());
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request,
        Map<String, String> headers) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        var options = defaultOptions.merge(request.getOptions(), headers);
        if (options.outputType() == DashScopeEmbeddingOptions.OutputType.SPARSE) {
            throw new IllegalArgumentException(
                "DashScope sparse-only embeddings cannot be represented as dense vectors; "
                    + "use dense or dense&sparse");
        }
        if (request.getInstructions() != null && request.getInstructions().size() > 20) {
            throw new IllegalArgumentException(
                "DashScope native embedding accepts at most 20 texts per request");
        }
        var body = requestBody(request.getInstructions(), options);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + EMBEDDING_PATH;
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("dashscope-embedding")
            .apiKey(options.apiKey())
            .headers(options.customHeaders())
            .timeout(options.timeout())
            .build();
        return embeddingResponse(transport.post(exchange), options.model());
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        var response = call(new org.springframework.ai.embedding.EmbeddingRequest(
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaultOptions));
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private Map<String, Object> requestBody(List<String> texts,
        DashScopeEmbeddingOptions options) {
        var parameters = new LinkedHashMap<String, Object>();
        if (options.dimensions() != null) {
            parameters.put("dimension", options.dimensions());
        }
        if (options.textType() != null) {
            parameters.put("text_type", options.textType().value());
        }
        parameters.put("output_type", options.outputType().value());
        if (hasText(options.instruct())) {
            parameters.put("instruct", options.instruct());
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("input", Map.of("texts", texts != null ? texts : List.of()));
        body.put("parameters", parameters);
        return body;
    }

    private EmbeddingResponse embeddingResponse(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var values = root.path("output").path("embeddings");
            var embeddings = IndexedEmbeddingDecoder.decode(values,
                item -> item.path("text_index").isNumber()
                    ? item.path("text_index").asInt() : -1,
                value -> IndexedEmbeddingDecoder.floatArray(value,
                    "DashScope embedding vector must be an array"));
            var sparse = new ArrayList<Map<String, Object>>();
            var fallbackIndex = 0;
            for (var item : values) {
                var index = item.path("text_index").isNumber()
                    ? item.path("text_index").asInt() : fallbackIndex;
                if (item.path("sparse_embedding").isArray()) {
                    sparse.add(Map.of(
                        "textIndex", index,
                        "values", OBJECT_MAPPER.convertValue(item.path("sparse_embedding"),
                            Object.class)
                    ));
                }
                fallbackIndex++;
            }
            sparse.sort(Comparator.comparingInt(value -> (Integer) value.get("textIndex")));

            var usage = root.path("usage");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var totalTokens = usage.path("total_tokens").isNumber()
                ? usage.path("total_tokens").asInt() : 0;
            var metadataValues = new LinkedHashMap<String, Object>();
            if (hasText(root.path("request_id").asText(null))) {
                metadataValues.put("id", root.path("request_id").asText());
            }
            if (!sparse.isEmpty()) {
                metadataValues.put("sparseEmbeddings", List.copyOf(sparse));
            }
            var metadata = new EmbeddingResponseMetadata(requestedModel,
                new DefaultUsage(totalTokens, 0, totalTokens, rawUsage), metadataValues);
            return new EmbeddingResponse(embeddings, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse DashScope embedding response", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
