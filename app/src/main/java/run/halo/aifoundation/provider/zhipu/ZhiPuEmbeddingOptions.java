package run.halo.aifoundation.provider.zhipu;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

/** Connection and request-scoped options for the native BigModel embedding API. */
@Builder(toBuilder = true)
record ZhiPuEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Integer dimensions,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    ZhiPuEmbeddingOptions {
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

    ZhiPuEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(requestOptions, ZhiPuEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(ZhiPuEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(ZhiPuEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(ZhiPuEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(ZhiPuEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .dimensions(mergedDimensions)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }
}
