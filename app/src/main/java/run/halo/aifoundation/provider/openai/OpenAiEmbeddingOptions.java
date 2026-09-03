package run.halo.aifoundation.provider.openai;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingOptions;
import run.halo.aifoundation.provider.support.ProviderHeaders;

record OpenAiEmbeddingOptions(String baseUrl, String apiKey, String model, Integer dimensions,
                              String encodingFormat, String user,
                              Map<String, Object> extraBody,
                              Map<String, String> customHeaders, Duration timeout)
    implements run.halo.aifoundation.provider.support.ParameterMappedEmbeddingOptions {

    OpenAiEmbeddingOptions {
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

    OpenAiEmbeddingOptions merge(EmbeddingOptions value, Map<String, String> requestHeaders) {
        var request = value instanceof OpenAiEmbeddingOptions options ? options : null;
        var headers = new LinkedHashMap<>(customHeaders);
        if (request != null) {
            headers.putAll(request.customHeaders());
        }
        if (requestHeaders != null) {
            requestHeaders.forEach((name, header) ->
                ProviderHeaders.putIfValid(headers, name, header));
        }
        var merged = new MutableOptions(this);
        merged.apply(value);
        merged.apply(request);
        return merged.toOptions(Map.copyOf(headers));
    }

    @Override
    public EmbeddingOptions withMappedBody(Map<String, Object> mappedBody) {
        var merged = new LinkedHashMap<>(extraBody);
        if (mappedBody != null) {
            merged.putAll(mappedBody);
        }
        return new OpenAiEmbeddingOptions(baseUrl, apiKey, model, null, encodingFormat, user,
            Map.copyOf(merged), customHeaders, timeout);
    }

    private static boolean text(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }

    private static final class MutableOptions {

        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions;
        private String encodingFormat;
        private String user;
        private Map<String, Object> extraBody;
        private Duration timeout;

        private MutableOptions(OpenAiEmbeddingOptions defaults) {
            baseUrl = defaults.baseUrl();
            apiKey = defaults.apiKey();
            model = defaults.model();
            dimensions = defaults.dimensions();
            encodingFormat = defaults.encodingFormat();
            user = defaults.user();
            extraBody = defaults.extraBody();
            timeout = defaults.timeout();
        }

        private void apply(EmbeddingOptions request) {
            if (request == null) {
                return;
            }
            if (text(request.getModel())) {
                model = request.getModel();
            }
            if (request.getDimensions() != null) {
                dimensions = request.getDimensions();
            }
        }

        private void apply(OpenAiEmbeddingOptions request) {
            if (request == null) {
                return;
            }
            if (text(request.baseUrl())) {
                baseUrl = request.baseUrl();
            }
            if (text(request.apiKey())) {
                apiKey = request.apiKey();
            }
            if (request.encodingFormat() != null) {
                encodingFormat = request.encodingFormat();
            }
            if (request.user() != null) {
                user = request.user();
            }
            if (!request.extraBody().isEmpty()) {
                extraBody = request.extraBody();
            }
            timeout = request.timeout();
        }

        private OpenAiEmbeddingOptions toOptions(Map<String, String> headers) {
            return new OpenAiEmbeddingOptions(baseUrl, apiKey, model, dimensions, encodingFormat,
                user, extraBody, headers, timeout);
        }
    }
}
