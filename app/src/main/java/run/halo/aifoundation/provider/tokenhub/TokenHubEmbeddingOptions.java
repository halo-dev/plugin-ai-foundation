package run.halo.aifoundation.provider.tokenhub;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

/** Connection and request-scoped options for TokenHub text embeddings. */
@Builder(toBuilder = true)
record TokenHubEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    TokenHubEmbeddingOptions {
        customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getDimensions() {
        // TokenHub fixes the output dimension per model.
        return null;
    }

    TokenHubEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(requestOptions,
            TokenHubEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(TokenHubEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(TokenHubEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(TokenHubEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(TokenHubEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }
}
