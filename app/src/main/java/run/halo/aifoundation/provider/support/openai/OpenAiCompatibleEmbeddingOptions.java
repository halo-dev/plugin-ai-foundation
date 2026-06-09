package run.halo.aifoundation.provider.support.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.lang.Nullable;

public class OpenAiCompatibleEmbeddingOptions implements EmbeddingOptions {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    public static final int DEFAULT_MAX_RETRIES = 3;

    private final @Nullable String baseUrl;
    private final @Nullable String apiKey;
    private final @Nullable String model;
    private final @Nullable String deploymentName;
    private final Duration timeout;
    private final int maxRetries;
    private final @Nullable Proxy proxy;
    private final @Nullable Map<String, String> customHeaders;
    private final @Nullable String user;
    private final @Nullable EncodingFormat encodingFormat;
    private final @Nullable Integer dimensions;

    private OpenAiCompatibleEmbeddingOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
        this.model = builder.model;
        this.deploymentName = builder.deploymentName;
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.maxRetries = builder.maxRetries != null ? builder.maxRetries : DEFAULT_MAX_RETRIES;
        this.proxy = builder.proxy;
        this.customHeaders = builder.customHeaders != null ? Map.copyOf(builder.customHeaders) : null;
        this.user = builder.user;
        this.encodingFormat = builder.encodingFormat;
        this.dimensions = builder.dimensions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public @Nullable String getBaseUrl() {
        return baseUrl;
    }

    public @Nullable String getApiKey() {
        return apiKey;
    }

    @Override
    public @Nullable String getModel() {
        return model;
    }

    public @Nullable String getDeploymentName() {
        return deploymentName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public @Nullable Proxy getProxy() {
        return proxy;
    }

    public @Nullable Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public @Nullable String getUser() {
        return user;
    }

    public @Nullable EncodingFormat getEncodingFormat() {
        return encodingFormat;
    }

    @Override
    public @Nullable Integer getDimensions() {
        return dimensions;
    }

    public enum EncodingFormat {
        FLOAT("float"), BASE64("base64");

        private final String value;

        EncodingFormat(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class Builder {

        private @Nullable String baseUrl;
        private @Nullable String apiKey;
        private @Nullable String model;
        private @Nullable String deploymentName;
        private @Nullable Duration timeout;
        private @Nullable Integer maxRetries;
        private @Nullable Proxy proxy;
        private @Nullable Map<String, String> customHeaders;
        private @Nullable String user;
        private @Nullable EncodingFormat encodingFormat;
        private @Nullable Integer dimensions;

        public Builder baseUrl(@Nullable String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(@Nullable String model) {
            this.model = model;
            return this;
        }

        public Builder deploymentName(@Nullable String deploymentName) {
            this.deploymentName = deploymentName;
            return this;
        }

        public Builder timeout(@Nullable Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(@Nullable Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder proxy(@Nullable Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder customHeaders(@Nullable Map<String, String> customHeaders) {
            this.customHeaders = customHeaders != null ? new HashMap<>(customHeaders) : null;
            return this;
        }

        public Builder user(@Nullable String user) {
            this.user = user;
            return this;
        }

        public Builder encodingFormat(@Nullable EncodingFormat encodingFormat) {
            this.encodingFormat = encodingFormat;
            return this;
        }

        public Builder dimensions(@Nullable Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public OpenAiCompatibleEmbeddingOptions build() {
            return new OpenAiCompatibleEmbeddingOptions(this);
        }
    }
}
