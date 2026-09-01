package run.halo.aifoundation.provider.openrouter;

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

/** Native OpenRouter embeddings router client with routing and cost metadata. */
public final class OpenRouterEmbeddingModel
    implements ProviderEmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenRouterEmbeddingOptions defaults;
    private final EmbeddingHttpTransport transport;

    OpenRouterEmbeddingModel(OpenRouterEmbeddingOptions defaults,
        WebClient.Builder webClientBuilder) {
        this.defaults = defaults;
        this.transport = new EmbeddingHttpTransport("openrouter", webClientBuilder);
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
        var options = merged(request.options(), request.headers());
        var input = embeddingInput(request);
        var body = requestBody(input, options);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("openrouter-embedding")
            .apiKey(options.apiKey())
            .headers(options.customHeaders())
            .timeout(options.timeout())
            .build();
        return response(transport.post(exchange), options.getModel());
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        var response = call(new org.springframework.ai.embedding.EmbeddingRequest(
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaults));
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private OpenRouterEmbeddingOptions merged(org.springframework.ai.embedding.EmbeddingOptions raw,
        Map<String, String> headers) {
        var builder = defaults.mutate();
        if (raw != null) {
            builder.model(raw.getModel() != null ? raw.getModel() : defaults.getModel())
                .dimensions(raw.getDimensions() != null
                    ? raw.getDimensions() : defaults.getDimensions());
        }
        var customHeaders = new LinkedHashMap<>(defaults.customHeaders());
        if (raw instanceof OpenRouterEmbeddingOptions options) {
            if (options.encodingFormat() != null) {
                builder.encodingFormat(options.encodingFormat());
            }
            if (options.inputType() != null) {
                builder.inputType(options.inputType());
            }
            if (options.user() != null) {
                builder.user(options.user());
            }
            if (!options.provider().isEmpty()) {
                builder.provider(options.provider());
            }
            customHeaders.putAll(options.customHeaders());
        }
        if (headers != null) {
            customHeaders.putAll(headers);
        }
        return builder.customHeaders(customHeaders).build();
    }

    private Object embeddingInput(ProviderEmbeddingRequest request) {
        if (!request.contents().isEmpty()) {
            return multimodalInput(request.contents());
        }
        return request.inputs();
    }

    private List<Map<String, Object>> multimodalInput(List<EmbeddingContent> contents) {
        var parts = new ArrayList<Map<String, Object>>();
        for (var content : contents) {
            parts.add(multimodalPart(content));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException(
                "OpenRouter multimodal embedding content must not be empty");
        }
        return List.of(Map.of("content", List.copyOf(parts)));
    }

    private Map<String, Object> multimodalPart(EmbeddingContent content) {
        if (content == null) {
            throw new IllegalArgumentException(
                "OpenRouter multimodal embedding content must not contain null");
        }
        return switch (content.getType()) {
            case TEXT -> Map.of("type", "text", "text", content.getText());
            case IMAGE -> Map.of("type", "image_url", "image_url", Map.of("url",
                MediaContentSources.urlOrDataUrl(content.getMedia(),
                    "OpenRouter embedding image")));
            case VIDEO -> throw new IllegalArgumentException(
                "OpenRouter embeddings do not document video input");
        };
    }

    private Map<String, Object> requestBody(Object input,
        OpenRouterEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.getModel());
        body.put("input", input != null ? input : List.of());
        put(body, "dimensions", options.getDimensions());
        put(body, "encoding_format", options.encodingFormat());
        put(body, "input_type", options.inputType());
        put(body, "user", options.user());
        if (!options.provider().isEmpty()) {
            body.put("provider", options.provider());
        }
        return body;
    }

    private EmbeddingResponse response(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var embeddings = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "OpenRouter embedding vector has an invalid shape"));
            var usage = root.path("usage");
            var promptTokens = integer(usage.path("prompt_tokens"));
            var totalTokens = integer(usage.path("total_tokens"));
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var metadataValues = new LinkedHashMap<String, Object>();
            put(metadataValues, "id", text(root.path("id")));
            put(metadataValues, "provider", text(root.path("provider")));
            if (rawUsage instanceof Map<?, ?> map && map.get("cost") != null) {
                metadataValues.put("cost", map.get("cost"));
            }
            var metadata = new EmbeddingResponseMetadata(
                hasText(text(root.path("model"))) ? text(root.path("model")) : requestedModel,
                new DefaultUsage(promptTokens, 0, totalTokens, rawUsage), metadataValues);
            return new EmbeddingResponse(embeddings, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse OpenRouter embedding response", e);
        }
    }

    private Integer integer(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : 0;
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            return;
        }
        map.put(key, value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
