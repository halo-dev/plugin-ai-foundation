package run.halo.aifoundation.provider.dashscope;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.EmbeddingOptionOverrides;
import run.halo.aifoundation.provider.support.ProviderHeaders;

@Builder(toBuilder = true)
public record DashScopeEmbeddingOptions(
    String baseUrl,
    String apiKey,
    String model,
    Integer dimensions,
    TextType textType,
    OutputType outputType,
    String instruct,
    Map<String, String> customHeaders,
    Duration timeout
) implements EmbeddingOptions {

    public DashScopeEmbeddingOptions {
        customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        outputType = outputType == null ? OutputType.DENSE : outputType;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getDimensions() {
        return dimensions;
    }

    public DashScopeEmbeddingOptions merge(EmbeddingOptions requestOptions,
        Map<String, String> requestHeaders) {
        var overrides = EmbeddingOptionOverrides.from(
            requestOptions, DashScopeEmbeddingOptions.class);
        var mergedBaseUrl = overrides.textOr(DashScopeEmbeddingOptions::baseUrl, baseUrl);
        var mergedApiKey = overrides.textOr(DashScopeEmbeddingOptions::apiKey, apiKey);
        var mergedModel = overrides.modelOr(model);
        var mergedDimensions = overrides.dimensionsOr(dimensions);
        var mergedTextType = overrides.valueOr(DashScopeEmbeddingOptions::textType, textType);
        var mergedOutputType = overrides.valueOr(DashScopeEmbeddingOptions::outputType, outputType);
        var mergedInstruct = overrides.textOr(DashScopeEmbeddingOptions::instruct, instruct);
        var mergedHeaders = ProviderHeaders.merge(customHeaders,
            overrides.providerValue(DashScopeEmbeddingOptions::customHeaders), requestHeaders);
        var mergedTimeout = overrides.valueOr(DashScopeEmbeddingOptions::timeout, timeout);

        return toBuilder()
            .baseUrl(mergedBaseUrl)
            .apiKey(mergedApiKey)
            .model(mergedModel)
            .dimensions(mergedDimensions)
            .textType(mergedTextType)
            .outputType(mergedOutputType)
            .instruct(mergedInstruct)
            .customHeaders(mergedHeaders)
            .timeout(mergedTimeout)
            .build();
    }

    public enum TextType {
        QUERY("query"), DOCUMENT("document");

        private final String value;

        TextType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum OutputType {
        DENSE("dense"), SPARSE("sparse"), DENSE_AND_SPARSE("dense&sparse");

        private final String value;

        OutputType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
