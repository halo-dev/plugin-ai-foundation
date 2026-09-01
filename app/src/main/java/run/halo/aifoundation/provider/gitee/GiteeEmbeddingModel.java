package run.halo.aifoundation.provider.gitee;

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

/** Gitee AI text and multimodal embedding adapter. */
public final class GiteeEmbeddingModel implements ProviderEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_INPUTS = 1000;

    private final GiteeEmbeddingOptions defaultOptions;
    private final EmbeddingHttpTransport transport;

    public GiteeEmbeddingModel(GiteeEmbeddingOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaultOptions = defaultOptions;
        this.transport = new EmbeddingHttpTransport("gitee-moark", webClientBuilder);
    }

    public GiteeEmbeddingOptions getOptions() {
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
        validateOptions(options);
        var input = request.contents().isEmpty()
            ? textInput(request.inputs()) : multimodalInput(request.contents());
        var body = new LinkedHashMap<>(options.extraBody());
        body.put("model", options.model());
        body.put("input", input);
        body.putIfAbsent("encoding_format", "float");
        if (options.dimensions() != null) {
            body.put("dimensions", options.dimensions());
        }
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("gitee-embedding")
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

    private void validateOptions(GiteeEmbeddingOptions options) {
        if (hasText(options.instructions())) {
            throw new IllegalArgumentException("Gitee AI embeddings do not support instructions");
        }
        if (options.includeSparseEmbedding() || options.includeModalityEmbeddings()) {
            throw new IllegalArgumentException(
                "Gitee AI embeddings return dense vectors without per-modality vectors");
        }
    }

    private List<String> textInput(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Gitee AI embedding input must not be empty");
        }
        validateCount(inputs.size());
        if (inputs.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Gitee AI embedding text must not be blank");
        }
        return List.copyOf(inputs);
    }

    private List<Map<String, String>> multimodalInput(List<EmbeddingContent> contents) {
        validateCount(contents.size());
        var input = new ArrayList<Map<String, String>>();
        for (var content : contents) {
            switch (content.getType()) {
                case TEXT -> input.add(Map.of("text", content.getText()));
                case IMAGE -> input.add(Map.of("image", imageSource(content)));
                case VIDEO -> throw new IllegalArgumentException(
                    "Gitee AI multimodal embeddings do not support video input");
            }
        }
        return List.copyOf(input);
    }

    private String imageSource(EmbeddingContent content) {
        var media = content.getMedia();
        if (media == null || media.isUrl() == media.isData()) {
            throw new IllegalArgumentException(
                "Gitee AI embedding image must set exactly one URL or base64 value");
        }
        return media.isUrl() ? media.getUrl()
            : "data:" + media.getMediaType() + ";base64," + media.getData();
    }

    private void validateCount(int size) {
        if (size < 1 || size > MAX_INPUTS) {
            throw new IllegalArgumentException(
                "Gitee AI embeddings accept between 1 and 1000 inputs per request");
        }
    }

    private EmbeddingResponse embeddingResponse(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var indexed = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "Gitee AI embedding vector has an invalid shape"));
            var usage = root.path("usage");
            var promptTokens = integer(usage, "prompt_tokens");
            var totalTokens = integer(usage, "total_tokens");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var metadata = new EmbeddingResponseMetadata(
                hasText(text(root, "model")) ? text(root, "model") : requestedModel,
                new DefaultUsage(promptTokens, 0, totalTokens, rawUsage), Map.of());
            return new EmbeddingResponse(indexed, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse Gitee AI embedding response", e);
        }
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asInt() : 0;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
