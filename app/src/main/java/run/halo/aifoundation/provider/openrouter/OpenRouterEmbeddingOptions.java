package run.halo.aifoundation.provider.openrouter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;

/** Connection and request-scoped options for OpenRouter's embeddings router. */
final class OpenRouterEmbeddingOptions implements EmbeddingOptions {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Integer dimensions;
    private final String encodingFormat;
    private final String inputType;
    private final String user;
    private final Map<String, Object> provider;
    private final Map<String, String> customHeaders;
    private final Duration timeout;

    private OpenRouterEmbeddingOptions(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.apiKey = builder.apiKey;
        this.model = builder.model;
        this.dimensions = builder.dimensions;
        this.encodingFormat = builder.encodingFormat;
        this.inputType = builder.inputType;
        this.user = builder.user;
        this.provider = builder.provider != null ? Map.copyOf(builder.provider) : Map.of();
        this.customHeaders = builder.customHeaders != null
            ? Map.copyOf(builder.customHeaders) : Map.of();
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public Integer getDimensions() {
        return dimensions;
    }

    String baseUrl() {
        return baseUrl;
    }

    String apiKey() {
        return apiKey;
    }

    String encodingFormat() {
        return encodingFormat;
    }

    String inputType() {
        return inputType;
    }

    String user() {
        return user;
    }

    Map<String, Object> provider() {
        return provider;
    }

    Map<String, String> customHeaders() {
        return customHeaders;
    }

    Duration timeout() {
        return timeout;
    }

    Builder mutate() {
        return builder().baseUrl(baseUrl).apiKey(apiKey).model(model).dimensions(dimensions)
            .encodingFormat(encodingFormat).inputType(inputType).user(user).provider(provider)
            .customHeaders(customHeaders).timeout(timeout);
    }

    static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions;
        private String encodingFormat;
        private String inputType;
        private String user;
        private Map<String, Object> provider;
        private Map<String, String> customHeaders;
        private Duration timeout;

        Builder baseUrl(String value) { this.baseUrl = value; return this; }
        Builder apiKey(String value) { this.apiKey = value; return this; }
        Builder model(String value) { this.model = value; return this; }
        Builder dimensions(Integer value) { this.dimensions = value; return this; }
        Builder encodingFormat(String value) { this.encodingFormat = value; return this; }
        Builder inputType(String value) { this.inputType = value; return this; }
        Builder user(String value) { this.user = value; return this; }
        Builder provider(Map<String, Object> value) {
            this.provider = value != null ? new LinkedHashMap<>(value) : null;
            return this;
        }
        Builder customHeaders(Map<String, String> value) {
            this.customHeaders = value != null ? new LinkedHashMap<>(value) : null;
            return this;
        }
        Builder timeout(Duration value) { this.timeout = value; return this; }
        OpenRouterEmbeddingOptions build() { return new OpenRouterEmbeddingOptions(this); }
    }
}
