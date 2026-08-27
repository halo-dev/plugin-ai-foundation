package run.halo.aifoundation.provider.support.embedding;

import java.time.Duration;
import java.util.Map;

/** Immutable description of one provider embedding HTTP exchange. */
public final class EmbeddingHttpRequest {

    private final String url;
    private final String adapterType;
    private final String operation;
    private final String apiKey;
    private final Map<String, String> headers;
    private final Object body;
    private final Duration timeout;

    private EmbeddingHttpRequest(Builder builder) {
        this.url = builder.url;
        this.adapterType = builder.adapterType;
        this.operation = builder.operation;
        this.apiKey = builder.apiKey;
        this.headers = builder.headers == null ? Map.of() : Map.copyOf(builder.headers);
        this.body = builder.body;
        this.timeout = builder.timeout;
    }

    public static Builder builder(String url, Object body) {
        return new Builder(url, body);
    }

    public String url() {
        return url;
    }

    public String adapterType() {
        return adapterType;
    }

    public String operation() {
        return operation;
    }

    public String apiKey() {
        return apiKey;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public Object body() {
        return body;
    }

    public Duration timeout() {
        return timeout;
    }

    public static final class Builder {

        private final String url;
        private final Object body;
        private String adapterType = "embedding";
        private String operation = "embedding";
        private String apiKey;
        private Map<String, String> headers = Map.of();
        private Duration timeout;

        private Builder(String url, Object body) {
            this.url = url;
            this.body = body;
        }

        public Builder adapterType(String adapterType) {
            this.adapterType = adapterType;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public EmbeddingHttpRequest build() {
            return new EmbeddingHttpRequest(this);
        }
    }
}
