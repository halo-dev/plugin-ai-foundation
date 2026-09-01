package run.halo.aifoundation.provider.ernie;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

@Builder(toBuilder = true)
public record ErnieEmbeddingOptions(
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

    public ErnieEmbeddingOptions {
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

    ErnieEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(requestOptions, ErnieEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(ErnieEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(ErnieEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedInstructions = overrides.textOr(
            ErnieEmbeddingOptions::instructions, instructions);
        var includeSparse = overrides.booleanOr(
            ErnieEmbeddingOptions::includeSparseEmbedding, includeSparseEmbedding);
        var includeModalities = overrides.booleanOr(
            ErnieEmbeddingOptions::includeModalityEmbeddings, includeModalityEmbeddings);
        var body = new LinkedHashMap<>(extraBody);
        var requestBody = overrides.providerValue(ErnieEmbeddingOptions::extraBody);
        if (requestBody != null) {
            body.putAll(requestBody);
        }
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(ErnieEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(ErnieEmbeddingOptions::timeout, timeout);

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
