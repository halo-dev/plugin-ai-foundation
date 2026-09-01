package run.halo.aifoundation.provider.zhipu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;

/** Native BigModel text embedding client. */
public final class ZhiPuEmbeddingModel
    implements EmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ZhiPuEmbeddingOptions defaults;
    private final EmbeddingHttpTransport transport;

    ZhiPuEmbeddingModel(ZhiPuEmbeddingOptions defaults, WebClient.Builder webClientBuilder) {
        Assert.notNull(defaults, "defaults must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaults = defaults;
        this.transport = new EmbeddingHttpTransport("zhipuai", webClientBuilder);
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        return call(request, Map.of());
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request,
        Map<String, String> headers) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        var options = defaults.merge(request.getOptions(), headers);
        validate(request.getInstructions(), options);
        var body = requestBody(request.getInstructions(), options);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("zhipu-embedding")
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
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaults));
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private void validate(List<String> input, ZhiPuEmbeddingOptions options) {
        if (input == null) {
            throw new IllegalArgumentException(
                "Zhipu embedding input must contain non-blank text");
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException(
                "Zhipu embedding input must contain non-blank text");
        }
        if (input.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException(
                "Zhipu embedding input must contain non-blank text");
        }
        if (input.size() > 64) {
            throw new IllegalArgumentException(
                "Zhipu embedding accepts at most 64 inputs per request");
        }
        if (options.dimensions() != null && options.dimensions() < 1) {
            throw new IllegalArgumentException("Zhipu embedding dimensions must be positive");
        }
    }

    private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        return value.isBlank();
    }

    private Map<String, Object> requestBody(List<String> input, ZhiPuEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("input", List.copyOf(input));
        if (options.dimensions() != null) {
            body.put("dimensions", options.dimensions());
        }
        return body;
    }

    private EmbeddingResponse embeddingResponse(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var embeddings = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArray(value,
                    "Zhipu embedding vector must be an array"));
            var usage = root.path("usage");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var model = root.path("model").isTextual()
                ? root.path("model").asText() : requestedModel;
            var metadata = new EmbeddingResponseMetadata(model,
                new DefaultUsage(integer(usage.path("prompt_tokens")),
                    integer(usage.path("completion_tokens")),
                    integer(usage.path("total_tokens")), rawUsage), Map.of());
            return new EmbeddingResponse(embeddings, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse Zhipu embedding response", e);
        }
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
