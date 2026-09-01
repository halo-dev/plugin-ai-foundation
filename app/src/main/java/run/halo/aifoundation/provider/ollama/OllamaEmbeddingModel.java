package run.halo.aifoundation.provider.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
import run.halo.aifoundation.provider.support.RequestHeaderAwareEmbeddingModel;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;

/** Ollama's native {@code /api/embed} client. */
final class OllamaEmbeddingModel implements EmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final OllamaEmbeddingOptions defaults;
    private final EmbeddingHttpTransport transport;

    OllamaEmbeddingModel(String baseUrl, String apiKey, OllamaEmbeddingOptions defaults,
        WebClient.Builder webClientBuilder) {
        Assert.hasText(baseUrl, "baseUrl must not be blank");
        Assert.notNull(defaults, "defaults must not be null");
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaults = defaults;
        this.transport = new EmbeddingHttpTransport("ollama", webClientBuilder);
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        return call(request, Map.of());
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request,
        Map<String, String> requestHeaders) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        validate(request.getInstructions());
        var options = defaults.merge(request.getOptions());
        var body = requestBody(request.getInstructions(), options);
        var url = OllamaEndpoints.nativeUrl(baseUrl, "/embed");
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("ollama-embedding")
            .apiKey(apiKey)
            .headers(requestHeaders)
            .timeout(Duration.ofSeconds(60))
            .build();
        return response(transport.post(exchange), options.model());
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        var request = new org.springframework.ai.embedding.EmbeddingRequest(
            List.of(document.getFormattedContent(MetadataMode.EMBED)), defaults);
        var response = call(request);
        return response.getResults().isEmpty() ? new float[0] : response.getResult().getOutput();
    }

    private void validate(List<String> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Ollama embeddings require at least one input");
        }
        if (input.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Ollama embedding input must not be blank");
        }
    }

    private Map<String, Object> requestBody(List<String> input, OllamaEmbeddingOptions options) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("input", List.copyOf(input));
        put(body, "dimensions", options.dimensions());
        put(body, "truncate", options.truncate());
        if (!options.runtimeOptions().isEmpty()) {
            body.put("options", options.runtimeOptions());
        }
        return Map.copyOf(body);
    }

    private EmbeddingResponse response(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var results = new ArrayList<Embedding>();
            var index = 0;
            for (var item : root.path("embeddings")) {
                var values = new float[item.size()];
                for (var dimension = 0; dimension < item.size(); dimension++) {
                    values[dimension] = (float) item.get(dimension).asDouble();
                }
                results.add(new Embedding(values, index++));
            }
            if (results.isEmpty()) {
                throw new IllegalStateException("Ollama embedding response contains no vectors");
            }
            var promptTokens = integer(root, "prompt_eval_count");
            var nativeUsage = new LinkedHashMap<String, Object>();
            for (var field : List.of("prompt_eval_count", "total_duration", "load_duration")) {
                if (root.path(field).isNumber()) {
                    nativeUsage.put(field, root.path(field).numberValue());
                }
            }
            var usage = new DefaultUsage(promptTokens, 0, promptTokens, Map.copyOf(nativeUsage));
            var model = hasText(root.path("model").asText())
                ? root.path("model").asText() : requestedModel;
            return new EmbeddingResponse(results,
                new EmbeddingResponseMetadata(model, usage, Map.of()));
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to parse Ollama embedding response", error);
        }
    }

    private Integer integer(com.fasterxml.jackson.databind.JsonNode root, String field) {
        return root.path(field).isNumber() ? root.path(field).asInt() : 0;
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
