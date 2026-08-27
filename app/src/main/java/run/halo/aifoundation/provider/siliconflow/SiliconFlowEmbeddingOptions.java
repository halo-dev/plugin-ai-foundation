package run.halo.aifoundation.provider.siliconflow;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

/** Connection and request-scoped options for SiliconFlow embeddings. */
@Builder(toBuilder = true)
record SiliconFlowEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Integer dimensions,
    String encodingFormat,
    String user,
    String truncate,
    String instructions,
    boolean includeSparseEmbedding,
    boolean includeModalityEmbeddings,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    SiliconFlowEmbeddingOptions {
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

    SiliconFlowEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(
            requestOptions, SiliconFlowEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(SiliconFlowEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(SiliconFlowEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedFormat = overrides.textOr(
            SiliconFlowEmbeddingOptions::encodingFormat, encodingFormat);
        var mergedUser = overrides.textOr(SiliconFlowEmbeddingOptions::user, user);
        var mergedTruncate = overrides.textOr(SiliconFlowEmbeddingOptions::truncate, truncate);
        var mergedInstructions = overrides.textOr(
            SiliconFlowEmbeddingOptions::instructions, instructions);
        var includeSparse = overrides.booleanOr(
            SiliconFlowEmbeddingOptions::includeSparseEmbedding, includeSparseEmbedding);
        var includeModalities = overrides.booleanOr(
            SiliconFlowEmbeddingOptions::includeModalityEmbeddings, includeModalityEmbeddings);
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(SiliconFlowEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(SiliconFlowEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .dimensions(mergedDimensions)
            .encodingFormat(mergedFormat)
            .user(mergedUser)
            .truncate(mergedTruncate)
            .instructions(mergedInstructions)
            .includeSparseEmbedding(includeSparse)
            .includeModalityEmbeddings(includeModalities)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }
}
