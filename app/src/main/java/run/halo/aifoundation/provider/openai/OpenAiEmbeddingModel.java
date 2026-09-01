package run.halo.aifoundation.provider.openai;

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
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;

/** OpenAI-owned embedding adapter for float and base64 vectors. */
public final class OpenAiEmbeddingModel
    implements EmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INVALID_INPUT =
        "OpenAI embedding input must contain 1 to 2048 non-blank strings";
    private final OpenAiEmbeddingOptions defaults;
    private final EmbeddingHttpTransport transport;

    OpenAiEmbeddingModel(OpenAiEmbeddingOptions defaults, WebClient.Builder builder) {
        this.defaults = defaults;
        this.transport = new EmbeddingHttpTransport("openai", builder);
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
            .adapterType("openai-embedding")
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

    private void validate(List<String> input, OpenAiEmbeddingOptions options) {
        if (input == null) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        if (input.size() > 2048) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        if (input.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        if (options.dimensions() == null) {
            return;
        }
        if (options.dimensions() < 1) {
            throw new IllegalArgumentException("OpenAI embedding dimensions must be positive");
        }
    }

    private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        return value.isBlank();
    }

    private Map<String, Object> requestBody(List<String> input, OpenAiEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>(options.extraBody());
        body.put("model", options.model());
        body.put("input", List.copyOf(input));
        put(body, "dimensions", options.dimensions());
        put(body, "encoding_format", options.encodingFormat());
        put(body, "user", options.user());
        return body;
    }

    private EmbeddingResponse embeddingResponse(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var values = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "OpenAI embedding vector has an invalid shape"));
            var usage = root.path("usage");
            var raw = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var model = root.path("model").isTextual() ? root.path("model").asText()
                : requestedModel;
            return new EmbeddingResponse(values, new EmbeddingResponseMetadata(model,
                new DefaultUsage(
                    integer(usage.path("prompt_tokens")), 0,
                    integer(usage.path("total_tokens")), raw), Map.of()));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse OpenAI embedding response", e);
        }
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : 0;
    }

    private void put(Map<String, Object> body, String field, Object value) {
        if (value == null) {
            return;
        }
        body.put(field, value);
    }

    private boolean text(String value) {
        return value != null && !value.isBlank();
    }

}
