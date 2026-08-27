package run.halo.aifoundation.provider.doubao;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

@Builder(toBuilder = true)
public record DouBaoEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Integer dimensions,
    String instructions,
    boolean includeSparseEmbedding,
    boolean includeModalityEmbeddings,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    public DouBaoEmbeddingOptions {
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

    DouBaoEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(
            requestOptions, DouBaoEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(DouBaoEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(DouBaoEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedInstructions = overrides.textOr(
            DouBaoEmbeddingOptions::instructions, instructions);
        var includeSparse = overrides.booleanOr(
            DouBaoEmbeddingOptions::includeSparseEmbedding, includeSparseEmbedding);
        var includeModalities = overrides.booleanOr(
            DouBaoEmbeddingOptions::includeModalityEmbeddings, includeModalityEmbeddings);
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(DouBaoEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(DouBaoEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .dimensions(mergedDimensions)
            .instructions(mergedInstructions)
            .includeSparseEmbedding(includeSparse)
            .includeModalityEmbeddings(includeModalities)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }
}
