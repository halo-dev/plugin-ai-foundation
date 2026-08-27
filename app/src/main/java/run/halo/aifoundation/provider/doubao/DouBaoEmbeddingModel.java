package run.halo.aifoundation.provider.doubao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.provider.support.ProviderEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;

/** Volcano Ark text and multimodal embedding adapter. */
public final class DouBaoEmbeddingModel implements ProviderEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEXT_PATH = "/embeddings";
    private static final String MULTIMODAL_PATH = "/embeddings/multimodal";

    private final DouBaoEmbeddingOptions defaultOptions;
    private final EmbeddingHttpTransport transport;

    public DouBaoEmbeddingModel(DouBaoEmbeddingOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaultOptions = defaultOptions;
        this.transport = new EmbeddingHttpTransport("doubao", webClientBuilder);
    }

    public DouBaoEmbeddingOptions getOptions() {
        return defaultOptions;
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        return call(new ProviderEmbeddingRequest(request.getInstructions(), List.of(),
            request.getOptions(), Map.of()));
    }

    @Override
    public EmbeddingResponse call(ProviderEmbeddingRequest request) {
        Assert.notNull(request, "ProviderEmbeddingRequest must not be null");
        var options = defaultOptions.merge(request.options(), request.headers());
        var multimodal = !request.contents().isEmpty();
        if (!multimodal && requiresMultimodalInput(options)) {
            throw new IllegalArgumentException(
                "Doubao sparse, per-modality, and instruction options require multimodal contents");
        }
        var body = multimodal
            ? multimodalRequestBody(request.contents(), options)
            : textRequestBody(request.inputs(), options);
        var operation = multimodal ? "multimodal-embedding" : "embedding";
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl())
            + (multimodal ? MULTIMODAL_PATH : TEXT_PATH);
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("doubao-" + operation)
            .operation(operation)
            .apiKey(options.apiKey())
            .headers(options.customHeaders())
            .timeout(options.timeout())
            .build();
        return embeddingResponse(transport.post(exchange), multimodal, options.model());
    }

    private boolean requiresMultimodalInput(DouBaoEmbeddingOptions options) {
        if (options.includeSparseEmbedding()) {
            return true;
        }
        if (options.includeModalityEmbeddings()) {
            return true;
        }
        return hasText(options.instructions());
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        var response = call(new org.springframework.ai.embedding.EmbeddingRequest(
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaultOptions));
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private Map<String, Object> textRequestBody(List<String> inputs,
        DouBaoEmbeddingOptions options) {
        var body = baseBody(options);
        body.put("input", inputs != null ? inputs : List.of());
        return body;
    }

    private Map<String, Object> multimodalRequestBody(List<EmbeddingContent> contents,
        DouBaoEmbeddingOptions options) {
        var body = baseBody(options);
        body.put("input", contents.stream().map(this::content).toList());
        if (options.includeSparseEmbedding()) {
            body.put("sparse_embedding", Map.of("type", "enabled"));
        }
        if (options.includeModalityEmbeddings()) {
            body.put("multi_embedding", Map.of("type", "enabled"));
        }
        if (hasText(options.instructions())) {
            body.put("instructions", options.instructions());
        }
        return body;
    }

    private LinkedHashMap<String, Object> baseBody(DouBaoEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("encoding_format", "float");
        if (options.dimensions() != null) {
            body.put("dimensions", options.dimensions());
        }
        return body;
    }

    private Map<String, Object> content(EmbeddingContent content) {
        return switch (content.getType()) {
            case TEXT -> Map.of("type", "text", "text", content.getText());
            case IMAGE -> Map.of("type", "image_url", "image_url",
                Map.of("url", mediaUrl(content, "image")));
            case VIDEO -> Map.of("type", "video_url", "video_url",
                Map.of("url", mediaUrl(content, "video")));
        };
    }

    private String mediaUrl(EmbeddingContent content, String label) {
        if (content.getMedia() == null || !content.getMedia().isUrl()) {
            throw new IllegalArgumentException(
                "Doubao multimodal embedding " + label + " content requires a URL");
        }
        return content.getMedia().getUrl();
    }

    private EmbeddingResponse embeddingResponse(String data, boolean multimodal,
        String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var results = new ArrayList<Embedding>();
            var metadataValues = new LinkedHashMap<String, Object>();
            putText(metadataValues, "id", root.path("id"));
            if (multimodal) {
                var item = root.path("data");
                results.add(new Embedding(IndexedEmbeddingDecoder.floatArrayOrBase64(
                    item.path("embedding"), "Doubao embedding vector has an invalid shape"), 0));
                putNode(metadataValues, "sparseEmbedding", item.path("sparse_embedding"));
                putNode(metadataValues, "modalityEmbeddings", item.path("multi_embedding"));
            } else {
                results.addAll(IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                    IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                        "Doubao embedding vector has an invalid shape")));
            }
            var usage = root.path("usage");
            var promptTokens = usage.path("prompt_tokens").isNumber()
                ? usage.path("prompt_tokens").asInt() : 0;
            var totalTokens = usage.path("total_tokens").isNumber()
                ? usage.path("total_tokens").asInt() : promptTokens;
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var model = hasText(root.path("model").asText(null))
                ? root.path("model").asText() : requestedModel;
            var metadata = new EmbeddingResponseMetadata(model,
                new DefaultUsage(promptTokens, 0, totalTokens, rawUsage), metadataValues);
            return new EmbeddingResponse(List.copyOf(results), metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse Doubao embedding response", e);
        }
    }

    private void putText(Map<String, Object> target, String key, JsonNode value) {
        if (value == null) {
            return;
        }
        if (!value.isTextual()) {
            return;
        }
        if (value.asText().isBlank()) {
            return;
        }
        target.put(key, value.asText());
    }

    private void putNode(Map<String, Object> target, String key, JsonNode value) {
        if (JsonNodes.isAbsent(value)) {
            return;
        }
        if (value.isEmpty()) {
            return;
        }
        target.put(key, OBJECT_MAPPER.convertValue(value, Object.class));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
