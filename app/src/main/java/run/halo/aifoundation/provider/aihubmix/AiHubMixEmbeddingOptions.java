package run.halo.aifoundation.provider.aihubmix;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

/** Connection and request options for AIHubMix's embedding endpoint. */
@Builder(toBuilder = true)
record AiHubMixEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Integer dimensions,
    String embeddingFormat,
    String user,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    AiHubMixEmbeddingOptions {
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

    AiHubMixEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(
            requestOptions, AiHubMixEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(AiHubMixEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(AiHubMixEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedFormat = overrides.valueOr(
            AiHubMixEmbeddingOptions::embeddingFormat, embeddingFormat);
        var mergedUser = overrides.valueOr(AiHubMixEmbeddingOptions::user, user);
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(AiHubMixEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(AiHubMixEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .dimensions(mergedDimensions)
            .embeddingFormat(mergedFormat)
            .user(mergedUser)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }
}
