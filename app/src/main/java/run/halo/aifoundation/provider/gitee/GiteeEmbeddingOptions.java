package run.halo.aifoundation.provider.gitee;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

@Builder(toBuilder = true)
public record GiteeEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Integer dimensions,
    String instructions,
    boolean includeSparseEmbedding,
    boolean includeModalityEmbeddings,
    Map<String, Object> extraBody,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    public GiteeEmbeddingOptions {
        extraBody = extraBody == null ? Map.of() : Map.copyOf(extraBody);
        customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getDimensions() {
        return dimensions;
    }

    GiteeEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(requestOptions, GiteeEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(GiteeEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(GiteeEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedInstructions = overrides.textOr(
            GiteeEmbeddingOptions::instructions, instructions);
        var includeSparse = overrides.booleanOr(
            GiteeEmbeddingOptions::includeSparseEmbedding, includeSparseEmbedding);
        var includeModalities = overrides.booleanOr(
            GiteeEmbeddingOptions::includeModalityEmbeddings, includeModalityEmbeddings);
        var body = new LinkedHashMap<>(extraBody);
        var requestBody = overrides.providerValue(GiteeEmbeddingOptions::extraBody);
        if (requestBody != null) {
            body.putAll(requestBody);
        }
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(GiteeEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(GiteeEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .dimensions(mergedDimensions)
            .instructions(mergedInstructions)
            .includeSparseEmbedding(includeSparse)
            .includeModalityEmbeddings(includeModalities)
            .extraBody(body)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }
}
