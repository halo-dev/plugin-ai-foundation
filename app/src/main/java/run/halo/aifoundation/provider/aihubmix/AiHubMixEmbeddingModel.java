package run.halo.aifoundation.provider.aihubmix;

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

/** AIHubMix-owned dense embedding client. */
public final class AiHubMixEmbeddingModel
    implements ProviderEmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INVALID_INPUT =
        "AIHubMix embedding input must contain 1 to 2048 non-blank strings";

    private final AiHubMixEmbeddingOptions defaults;
    private final EmbeddingHttpTransport transport;

    AiHubMixEmbeddingModel(AiHubMixEmbeddingOptions defaults,
        WebClient.Builder webClientBuilder) {
        this.defaults = defaults;
        this.transport = new EmbeddingHttpTransport("aihubmix", webClientBuilder);
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
        var input = embeddingInput(request);
        var body = requestBody(input, options);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("aihubmix-embedding")
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
        validateCount(input == null ? 0 : input.size());
        if (input.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        return List.copyOf(input);
    }

    private List<Map<String, String>> multimodalInput(List<EmbeddingContent> contents) {
        validateCount(contents.size());
        var input = new ArrayList<Map<String, String>>();
        for (var content : contents) {
            input.add(multimodalItem(content));
        }
        return List.copyOf(input);
    }

    private Map<String, String> multimodalItem(EmbeddingContent content) {
        if (content == null) {
            throw new IllegalArgumentException(
                "AIHubMix multimodal embedding input must not contain null");
        }
        return switch (content.getType()) {
            case TEXT -> Map.of("text", content.getText());
            case IMAGE -> Map.of("image", MediaContentSources.urlOrBase64(content.getMedia(),
                "AIHubMix embedding image"));
            case VIDEO -> throw new IllegalArgumentException(
                "AIHubMix Jina embeddings do not document video input");
        };
    }

    private void validateCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
        if (count > 2048) {
            throw new IllegalArgumentException(INVALID_INPUT);
        }
    }

    private void validateOptions(AiHubMixEmbeddingOptions options) {
        if (options.dimensions() == null) {
            return;
        }
        if (options.dimensions() < 1) {
            throw new IllegalArgumentException(
                "AIHubMix embedding dimensions must be positive");
        }
    }

    private boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        return value.isBlank();
    }

    private Map<String, Object> requestBody(Object input,
        AiHubMixEmbeddingOptions options) {
        validateOptions(options);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("input", input);
        put(body, "dimensions", options.dimensions());
        put(body, "embedding_format", options.embeddingFormat());
        put(body, "user", options.user());
        return body;
    }

    private EmbeddingResponse response(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var values = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "AIHubMix embedding vector has an invalid shape"));
            var usage = root.path("usage");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var metadataValues = new LinkedHashMap<String, Object>();
            put(metadataValues, "id", text(root.path("id")));
            put(metadataValues, "provider", text(root.path("provider")));
            var model = hasText(text(root.path("model"))) ? text(root.path("model"))
                : requestedModel;
            var metadata = new EmbeddingResponseMetadata(model,
                new DefaultUsage(integer(usage.path("prompt_tokens")), 0,
                    integer(usage.path("total_tokens")), rawUsage), metadataValues);
            return new EmbeddingResponse(values, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse AIHubMix embedding response", e);
        }
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : 0;
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
