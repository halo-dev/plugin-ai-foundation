package run.halo.aifoundation.provider.ernie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
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

/** Qianfan v2 text and joint text-image embedding adapter. */
public final class ErnieEmbeddingModel implements ProviderEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_TEXT_INPUTS = 16;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final ErnieEmbeddingOptions defaultOptions;
    private final EmbeddingHttpTransport transport;

    public ErnieEmbeddingModel(ErnieEmbeddingOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaultOptions = defaultOptions;
        this.transport = new EmbeddingHttpTransport("ernie", webClientBuilder);
    }

    public ErnieEmbeddingOptions getOptions() {
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
        validateUnsupportedOptions(options);
        var multimodal = !request.contents().isEmpty();
        var body = multimodal
            ? multimodalRequestBody(request.contents(), options)
            : textRequestBody(request.inputs(), options);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("ernie-embedding")
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

    private void validateUnsupportedOptions(ErnieEmbeddingOptions options) {
        if (options.dimensions() != null) {
            throw new IllegalArgumentException("Qianfan v2 embeddings do not support dimensions");
        }
        if (hasText(options.instructions())) {
            throw new IllegalArgumentException("Qianfan v2 embeddings do not support instructions");
        }
        if (options.includeSparseEmbedding() || options.includeModalityEmbeddings()) {
            throw new IllegalArgumentException(
                "Qianfan v2 embeddings return only the joint dense embedding");
        }
    }

    private Map<String, Object> textRequestBody(List<String> inputs,
        ErnieEmbeddingOptions options) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Qianfan embedding input must not be empty");
        }
        if (inputs.size() > MAX_TEXT_INPUTS) {
            throw new IllegalArgumentException("Qianfan accepts at most " + MAX_TEXT_INPUTS
                + " embedding inputs per call");
        }
        if (inputs.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Qianfan embedding text must not be blank");
        }
        var body = baseBody(options);
        body.put("input", List.copyOf(inputs));
        return body;
    }

    private Map<String, Object> multimodalRequestBody(List<EmbeddingContent> contents,
        ErnieEmbeddingOptions options) {
        if (contents == null) {
            throw invalidMultimodalInput();
        }
        if (contents.isEmpty()) {
            throw invalidMultimodalInput();
        }
        if (contents.size() > 2) {
            throw invalidMultimodalInput();
        }
        var input = new LinkedHashMap<String, Object>();
        for (var content : contents) {
            switch (content.getType()) {
                case TEXT -> putUnique(input, "text", content.getText());
                case IMAGE -> putUnique(input, "image", imageSource(content));
                case VIDEO -> throw new IllegalArgumentException(
                    "Qianfan multimodal embeddings do not support video input");
            }
        }
        var body = baseBody(options);
        body.put("input", List.of(Map.copyOf(input)));
        return body;
    }

    private IllegalArgumentException invalidMultimodalInput() {
        return new IllegalArgumentException(
            "Qianfan multimodal embedding accepts one text, one image, or one text-image pair");
    }

    private void putUnique(Map<String, Object> input, String key, Object value) {
        if (input.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException(
                "Qianfan multimodal embedding accepts at most one " + key + " input");
        }
    }

    private String imageSource(EmbeddingContent content) {
        var media = content.getMedia();
        if (media == null || media.isUrl() == media.isData()) {
            throw new IllegalArgumentException(
                "Qianfan multimodal embedding image must set exactly one URL or base64 value");
        }
        if (media.isUrl()) {
            return media.getUrl();
        }
        var mediaType = media.getMediaType() != null
            ? media.getMediaType().toLowerCase(Locale.ROOT) : "";
        if (!List.of("image/jpeg", "image/jpg", "image/png", "image/bmp")
            .contains(mediaType)) {
            throw new IllegalArgumentException(
                "Qianfan multimodal embedding supports jpg, jpeg, png, and bmp images");
        }
        var bytes = Base64.getDecoder().decode(media.getData());
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(
                "Qianfan multimodal embedding image must not exceed 10 MB");
        }
        return "data:" + media.getMediaType() + ";base64," + media.getData();
    }

    private LinkedHashMap<String, Object> baseBody(ErnieEmbeddingOptions options) {
        var body = new LinkedHashMap<>(options.extraBody());
        body.put("model", options.model());
        body.put("encoding_format", "float");
        return body;
    }

    private EmbeddingResponse embeddingResponse(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var results = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArray(value,
                    "Qianfan embedding vector must be an array"));
            var usage = root.path("usage");
            var promptTokens = integer(usage, "prompt_tokens");
            var totalTokens = integer(usage, "total_tokens");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var metadataValues = new LinkedHashMap<String, Object>();
            put(metadataValues, "id", text(root, "id"));
            put(metadataValues, "object", text(root, "object"));
            if (root.path("created").isNumber()) {
                metadataValues.put("created", root.path("created").asLong());
            }
            var model = hasText(text(root, "model")) ? text(root, "model") : requestedModel;
            var metadata = new EmbeddingResponseMetadata(model,
                new DefaultUsage(promptTokens, 0, totalTokens, rawUsage), metadataValues);
            return new EmbeddingResponse(results, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse Qianfan embedding response", e);
        }
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : 0;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
