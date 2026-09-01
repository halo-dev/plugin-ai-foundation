package run.halo.aifoundation.provider.openailike;

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
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.ProviderHeaders;
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;

/**
 * OpenAI-compatible embedding adapter backed by WebClient.
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final OpenAiCompatibleEmbeddingOptions defaultOptions;
    private final EmbeddingHttpTransport transport;

    public OpenAiCompatibleEmbeddingModel(OpenAiCompatibleEmbeddingOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaultOptions = defaultOptions;
        this.transport = new EmbeddingHttpTransport("openai-compatible", webClientBuilder);
    }

    public OpenAiCompatibleEmbeddingOptions getOptions() {
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
        var options = mergedOptions(request.getOptions(), headers);
        var body = requestBody(request.getInstructions(), options);
        var url = embeddingsUrl(options);
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("openai-compatible-embedding")
            .apiKey(options.getApiKey())
            .headers(options.getCustomHeaders())
            .timeout(options.getTimeout())
            .build();
        return embeddingResponse(transport.post(exchange));
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        var response = call(new org.springframework.ai.embedding.EmbeddingRequest(
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaultOptions));
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private OpenAiCompatibleEmbeddingOptions mergedOptions(EmbeddingOptions options,
        Map<String, String> headers) {
        var builder = OpenAiCompatibleEmbeddingOptions.builder()
            .baseUrl(defaultOptions.getBaseUrl())
            .endpointPath(defaultOptions.getEndpointPath())
            .apiKey(defaultOptions.getApiKey())
            .model(defaultOptions.getModel())
            .deploymentName(defaultOptions.getDeploymentName())
            .timeout(defaultOptions.getTimeout())
            .maxRetries(defaultOptions.getMaxRetries())
            .proxy(defaultOptions.getProxy())
            .user(defaultOptions.getUser())
            .encodingFormat(defaultOptions.getEncodingFormat())
            .dimensions(defaultOptions.getDimensions())
            .extraBody(defaultOptions.getExtraBody());
        if (options != null) {
            if (options.getModel() != null) {
                builder.model(options.getModel());
            }
            if (options.getDimensions() != null) {
                builder.dimensions(options.getDimensions());
            }
        }
        var customHeaders = new LinkedHashMap<String, String>();
        if (defaultOptions.getCustomHeaders() != null) {
            customHeaders.putAll(defaultOptions.getCustomHeaders());
        }
        if (options instanceof OpenAiCompatibleEmbeddingOptions openAiOptions
            && openAiOptions.getCustomHeaders() != null) {
            customHeaders.putAll(openAiOptions.getCustomHeaders());
        }
        if (options instanceof OpenAiCompatibleEmbeddingOptions openAiOptions) {
            if (openAiOptions.getBaseUrl() != null) {
                builder.baseUrl(openAiOptions.getBaseUrl());
            }
            if (openAiOptions.getEndpointPath() != null) {
                builder.endpointPath(openAiOptions.getEndpointPath());
            }
            if (openAiOptions.getApiKey() != null) {
                builder.apiKey(openAiOptions.getApiKey());
            }
            if (openAiOptions.getDeploymentName() != null) {
                builder.deploymentName(openAiOptions.getDeploymentName());
            }
            if (openAiOptions.getTimeout() != null) {
                builder.timeout(openAiOptions.getTimeout());
            }
            builder.maxRetries(openAiOptions.getMaxRetries());
            if (openAiOptions.getProxy() != null) {
                builder.proxy(openAiOptions.getProxy());
            }
            if (openAiOptions.getUser() != null) {
                builder.user(openAiOptions.getUser());
            }
            if (openAiOptions.getEncodingFormat() != null) {
                builder.encodingFormat(openAiOptions.getEncodingFormat());
            }
            if (openAiOptions.getExtraBody() != null) {
                builder.extraBody(openAiOptions.getExtraBody());
            }
        }
        if (headers != null) {
            headers.forEach((name, value) ->
                ProviderHeaders.putIfValid(customHeaders, name, value));
        }
        if (!customHeaders.isEmpty()) {
            builder.customHeaders(Map.copyOf(customHeaders));
        }
        return builder.build();
    }

    private Map<String, Object> requestBody(List<String> input, OpenAiCompatibleEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>();
        body.put(Fields.INPUT, input != null ? input : List.of());
        body.put(Fields.MODEL, hasText(options.getDeploymentName())
            ? options.getDeploymentName()
            : options.getModel());
        putIfPresent(body, Fields.USER, options.getUser());
        if (options.getEncodingFormat() != null) {
            body.put(Fields.ENCODING_FORMAT, options.getEncodingFormat().getValue());
        }
        putIfPresent(body, Fields.DIMENSIONS, options.getDimensions());
        if (options.getExtraBody() != null) {
            body.putAll(options.getExtraBody());
        }
        return body;
    }

    private EmbeddingResponse embeddingResponse(String body) {
        try {
            var root = OBJECT_MAPPER.readTree(body);
            var embeddings = IndexedEmbeddingDecoder.decode(root.path(Fields.DATA), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "OpenAI-compatible embedding vector has an invalid shape"));
            var metadata = new EmbeddingResponseMetadata();
            metadata.setModel(root.path(Fields.MODEL).asText(""));
            metadata.setUsage(usage(root.path(Fields.USAGE)));
            return new EmbeddingResponse(embeddings, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse OpenAI-compatible embedding response", e);
        }
    }

    private DefaultUsage usage(JsonNode node) {
        if (JsonNodes.isAbsent(node)) {
            return new DefaultUsage(0, 0, 0, null);
        }
        var raw = OBJECT_MAPPER.convertValue(node, Object.class);
        return new DefaultUsage(
            node.path(Fields.PROMPT_TOKENS).isNumber()
                ? node.path(Fields.PROMPT_TOKENS).asInt()
                : 0,
            0,
            node.path(Fields.TOTAL_TOKENS).isNumber()
                ? node.path(Fields.TOTAL_TOKENS).asInt()
                : 0,
            raw
        );
    }

    private String embeddingsUrl(OpenAiCompatibleEmbeddingOptions options) {
        var baseUrl = options.getBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + endpointPath(options.getEndpointPath(), EMBEDDINGS_PATH);
    }

    private String endpointPath(String configuredPath, String defaultPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultPath;
        }
        return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class Fields {
        static final String DATA = "data";
        static final String DIMENSIONS = "dimensions";
        static final String EMBEDDING = "embedding";
        static final String ENCODING_FORMAT = "encoding_format";
        static final String INDEX = "index";
        static final String INPUT = "input";
        static final String MODEL = "model";
        static final String PROMPT_TOKENS = "prompt_tokens";
        static final String TOTAL_TOKENS = "total_tokens";
        static final String USAGE = "usage";
        static final String USER = "user";

        private Fields() {
        }
    }
}
