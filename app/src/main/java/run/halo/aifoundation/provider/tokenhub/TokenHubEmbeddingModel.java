package run.halo.aifoundation.provider.tokenhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.ProviderEmbeddingModel;
import run.halo.aifoundation.provider.support.ProviderEmbeddingRequest;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpRequest;
import run.halo.aifoundation.provider.support.embedding.EmbeddingHttpTransport;
import run.halo.aifoundation.provider.support.embedding.IndexedEmbeddingDecoder;
import run.halo.aifoundation.provider.usage.ProviderUsage;

/** TokenHub text embedding adapter using its OpenAI-compatible embeddings endpoint. */
public final class TokenHubEmbeddingModel implements ProviderEmbeddingModel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_INPUT_CHARACTERS = 2000;

    private final TokenHubEmbeddingOptions defaultOptions;
    private final EmbeddingHttpTransport transport;

    public TokenHubEmbeddingModel(TokenHubEmbeddingOptions defaultOptions,
        WebClient.Builder webClientBuilder) {
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        Assert.notNull(webClientBuilder, "webClientBuilder must not be null");
        this.defaultOptions = defaultOptions;
        this.transport = new EmbeddingHttpTransport("tokenhub", webClientBuilder);
    }

    public TokenHubEmbeddingOptions getOptions() {
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
        var input = textInput(request);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("input", input);
        body.put("encoding_format", "float");
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/embeddings";
        var exchange = EmbeddingHttpRequest.builder(url, body)
            .adapterType("tokenhub-embedding")
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

    private List<String> textInput(ProviderEmbeddingRequest request) {
        if (!request.contents().isEmpty()) {
            throw new IllegalArgumentException(
                "TokenHub multimodal embeddings use a separate endpoint this adapter does not "
                    + "implement");
        }
        var inputs = request.inputs();
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException(
                "TokenHub embedding input must contain non-blank text");
        }
        if (inputs.stream().anyMatch(this::isBlank)) {
            throw new IllegalArgumentException(
                "TokenHub embedding input must contain non-blank text");
        }
        if (inputs.stream().anyMatch(input -> input.length() > MAX_INPUT_CHARACTERS)) {
            throw new IllegalArgumentException(
                "TokenHub embedding input must be at most " + MAX_INPUT_CHARACTERS
                    + " characters per text");
        }
        return List.copyOf(inputs);
    }

    private EmbeddingResponse embeddingResponse(String data, String requestedModel) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var indexed = IndexedEmbeddingDecoder.decode(root.path("data"), value ->
                IndexedEmbeddingDecoder.floatArrayOrBase64(value,
                    "TokenHub embedding vector has an invalid shape"));
            var usage = root.path("usage");
            var rawUsage = JsonNodes.isAbsent(usage)
                ? null : OBJECT_MAPPER.convertValue(usage, Object.class);
            var metadata = new EmbeddingResponseMetadata(
                text(root, "model") != null ? text(root, "model") : requestedModel,
                ProviderUsage.embedding(usage, "prompt_tokens", "total_tokens", rawUsage),
                Map.of());
            return new EmbeddingResponse(indexed, metadata);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to parse TokenHub embedding response", e);
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
