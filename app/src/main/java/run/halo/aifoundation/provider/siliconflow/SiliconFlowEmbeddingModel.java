package run.halo.aifoundation.provider.siliconflow;

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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.embedding.EmbeddingContent;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.MediaContentSources;
import run.halo.aifoundation.provider.support.ProviderEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;

/** SiliconFlow classic and vision-language embedding client. */
public final class SiliconFlowEmbeddingModel
    implements ProviderEmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_INPUTS = 32;

    private final SiliconFlowEmbeddingOptions defaults;
    private final EmbeddingHttpTransport transport;

    SiliconFlowEmbeddingModel(SiliconFlowEmbeddingOptions defaults,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaults, "defaults must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaults = defaults;
        this.transport = new EmbeddingHttpTransport("siliconflow", webClientBuilder);
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        return call(request, Map.of());
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request,
        Map<String, String> headers) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        return call(new ProviderEmbeddingRequest(request.getInstructions(), List.of(),
            request.getOptions(), headers));
    }

    @Override
    public EmbeddingResponse call(ProviderEmbeddingRequest request) {
        Assert.notNull(request, "ProviderEmbeddingRequest must not be null");
        var options = defaults.merge(request.options(), request.headers());
        validateOptions(options);
        var input = embeddingInput(request);
        var body = requestBody(input, options);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("siliconflow-embedding")
            .apiKey(options.apiKey())
            .headers(options.customHeaders())
            .timeout(options.timeout())
            .build();
        return response(transport.post(exchange), options.model());
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        var response = call(new org.springframework.ai.embedding.EmbeddingRequest(
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaults));
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private Object embeddingInput(ProviderEmbeddingRequest request) {
        if (!request.contents().isEmpty()) {
            return multimodalInput(request.contents());
        }
        return textInput(request.inputs());
    }

    private List<String> textInput(List<String> input) {
        validateInputCount(input == null ? 0 : input.size());
        if (input.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException("SiliconFlow embedding input must not be blank");
        }
        return List.copyOf(input);
    }

    private List<Object> multimodalInput(List<EmbeddingContent> contents) {
        validateInputCount(contents.size());
        var input = new ArrayList<>();
        for (var content : contents) {
            input.add(multimodalItem(content));
        }
        return List.copyOf(input);
    }

    private Object multimodalItem(EmbeddingContent content) {
        if (content == null) {
            throw new IllegalArgumentException(
                "SiliconFlow multimodal embedding input must not contain null");
        }
        return switch (content.getType()) {
            case TEXT -> Map.of("text", content.getText());
            case IMAGE -> Map.of("image", MediaContentSources.urlOrBase64(content.getMedia(),
                "SiliconFlow embedding image"));
            case VIDEO -> throw new IllegalArgumentException(
                "SiliconFlow embeddings do not document video input");
        };
    }

    private void validateInputCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException(
                "SiliconFlow embeddings accept between 1 and 32 inputs per request");
        }
        if (count > MAX_INPUTS) {
            throw new IllegalArgumentException(
                "SiliconFlow embeddings accept between 1 and 32 inputs per request");
        }
    }

    private void validateOptions(SiliconFlowEmbeddingOptions options) {
        if (hasText(options.instructions())) {
            throw new IllegalArgumentException("SiliconFlow embeddings do not support instructions");
        }
        if (options.includeSparseEmbedding()) {
            throw new IllegalArgumentException(
                "SiliconFlow embeddings do not expose sparse or per-modality vectors");
        }
        if (options.includeModalityEmbeddings()) {
            throw new IllegalArgumentException(
                "SiliconFlow embeddings do not expose sparse or per-modality vectors");
        }
        validateDimensions(options.dimensions());
    }

    private void validateDimensions(Integer dimensions) {
        if (dimensions != null && dimensions < 1) {
            throw new IllegalArgumentException("SiliconFlow dimensions must be positive");
        }
    }

    private Map<String, Object> requestBody(Object input,
        SiliconFlowEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("input", input);
        if (hasText(options.encodingFormat())) {
            body.put("encoding_format", options.encodingFormat());
        }
        if (options.dimensions() != null) {
            body.put("dimensions", options.dimensions());
        }
        if (hasText(options.user())) {
            body.put("user", options.user());
        }
        if (hasText(options.truncate())) {
            body.put("truncate", options.truncate());
        }
        return body;
    }

    private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        return value.isBlank();
    }

    private EmbeddingResponse response(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var embeddings = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "Embedding vector must be an array or base64 string"));
            var usage = root.path("usage");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var promptTokens = integer(usage.path("prompt_tokens"));
            var completionTokens = integer(usage.path("completion_tokens"));
            var totalTokens = integer(usage.path("total_tokens"));
            var metadata = new EmbeddingResponseMetadata(
                hasText(text(root.path("model"))) ? text(root.path("model")) : requestedModel,
                new DefaultUsage(promptTokens, completionTokens, totalTokens, rawUsage), Map.of());
            return new EmbeddingResponse(embeddings, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse SiliconFlow embedding response", e);
        }
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : 0;
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
