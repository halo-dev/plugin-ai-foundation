package run.halo.aifoundation.provider.support;

import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

/**
 * OpenAI-compatible embedding model adapter that avoids Spring AI's
 * AbstractEmbeddingModel static classpath resource initialization.
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel, RequestHeaderAwareEmbeddingModel {

    private final OpenAiApi openAiApi;
    private final OpenAiEmbeddingOptions defaultOptions;

    public OpenAiCompatibleEmbeddingModel(OpenAiApi openAiApi, String modelId) {
        this(openAiApi, OpenAiEmbeddingOptions.builder().model(modelId).build());
    }

    public OpenAiCompatibleEmbeddingModel(OpenAiApi openAiApi,
        OpenAiEmbeddingOptions defaultOptions) {
        Assert.notNull(openAiApi, "openAiApi must not be null");
        Assert.notNull(defaultOptions, "defaultOptions must not be null");
        this.openAiApi = openAiApi;
        this.defaultOptions = defaultOptions;
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
        return call(request, Map.of());
    }

    @Override
    public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request,
        Map<String, String> headers) {
        Assert.notNull(request, "EmbeddingRequest must not be null");
        var options = mergeOptions(request.getOptions());
        var apiRequest = new OpenAiApi.EmbeddingRequest<>(
            request.getInstructions(),
            options.getModel(),
            options.getEncodingFormat(),
            options.getDimensions(),
            options.getUser()
        );
        var api = headers == null || headers.isEmpty()
            ? openAiApi
            : openAiApi.mutate().headers(httpHeaders(headers)).build();
        var responseEntity = api.embeddings(apiRequest);
        var response = responseEntity.getBody();
        if (response == null || response.data() == null) {
            return new EmbeddingResponse(List.of());
        }
        var usage = response.usage() != null
            ? new DefaultUsage(response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().totalTokens(),
                response.usage())
            : new EmptyUsage();
        var metadata = new EmbeddingResponseMetadata(response.model(), usage);
        var embeddings = response.data().stream()
            .map(item -> new Embedding(item.embedding(), item.index()))
            .toList();
        return new EmbeddingResponse(embeddings, metadata);
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        return embed(document.getFormattedContent(MetadataMode.EMBED));
    }

    private OpenAiEmbeddingOptions mergeOptions(EmbeddingOptions options) {
        if (options == null) {
            return defaultOptions;
        }
        var builder = OpenAiEmbeddingOptions.builder()
            .model(firstNonNull(options.getModel(), defaultOptions.getModel()))
            .dimensions(firstNonNull(options.getDimensions(), defaultOptions.getDimensions()));
        if (options instanceof OpenAiEmbeddingOptions openAiOptions) {
            builder.encodingFormat(firstNonNull(openAiOptions.getEncodingFormat(),
                    defaultOptions.getEncodingFormat()))
                .user(firstNonNull(openAiOptions.getUser(), defaultOptions.getUser()));
        } else {
            builder.encodingFormat(defaultOptions.getEncodingFormat())
                .user(defaultOptions.getUser());
        }
        return builder.build();
    }

    private static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private HttpHeaders httpHeaders(Map<String, String> headers) {
        var httpHeaders = new HttpHeaders();
        headers.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null) {
                httpHeaders.set(name, value);
            }
        });
        return httpHeaders;
    }
}
