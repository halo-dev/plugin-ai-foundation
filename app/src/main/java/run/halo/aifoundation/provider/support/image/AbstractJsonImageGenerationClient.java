package run.halo.aifoundation.provider.support.image;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;

abstract class AbstractJsonImageGenerationClient implements ProviderImageGenerationClient {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected final ImageGenerationClientOptions options;
    private final WebClient webClient;

    protected AbstractJsonImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        this.options = options;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(endpointUrl()))
            .headers(headers -> {
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (hasText(options.apiKey())) {
                    headers.setBearerAuth(options.apiKey());
                }
                options.customHeaders().forEach(headers::set);
                if (request.getHeaders() != null) {
                    request.getHeaders().forEach(headers::set);
                }
            })
            .bodyValue(requestBody(request))
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return errorBody(response)
                        .flatMap(error -> Mono.error(requestFailed(response, error)));
                }
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(data -> imageResponse(data, request));
            });
    }

    abstract Map<String, Object> requestBody(GenerateImageRequest request);

    abstract GenerateImageResult imageResponse(String data, GenerateImageRequest request);

    protected String endpointUrl() {
        return trimTrailingSlash(options.baseUrl()) + endpointPath();
    }

    protected abstract String endpointPath();

    protected Map<String, Object> providerOptions(GenerateImageRequest request) {
        if (request.getProviderOptions() == null) {
            return Map.of();
        }
        var values = request.getProviderOptions().get(options.providerType());
        return values == null ? Map.of() : values;
    }

    protected void putIfHasText(Map<String, Object> body, String key, String value) {
        if (hasText(value)) {
            body.put(key, value);
        }
    }

    protected void putIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    protected void putProviderOptions(Map<String, Object> body, GenerateImageRequest request) {
        body.putAll(providerOptions(request));
    }

    protected String imageSource(DataContent content) {
        if (content == null) {
            return null;
        }
        if (content.isUrl()) {
            return content.getUrl();
        }
        if (content.isData()) {
            return "data:" + content.getMediaType() + ";base64," + content.getData();
        }
        return null;
    }

    protected JsonNode readTree(String data, String providerLabel) {
        try {
            return OBJECT_MAPPER.readTree(data);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + providerLabel
                + " image response", e);
        }
    }

    protected GenerateImageResult result(String data, JsonNode root,
        List<run.halo.aifoundation.media.GeneratedFile> images, ImageUsage usage,
        List<ImageGenerationWarning> warnings, String id, String model) {
        return GenerateImageResult.builder()
            .images(images == null ? List.of() : images)
            .usage(usage == null ? ImageUsage.builder()
                .imageCount(images == null ? 0 : images.size())
                .build() : usage)
            .warnings(warnings == null ? List.of() : warnings)
            .responses(List.of(responseMetadata(data, root, id, model)))
            .providerMetadata(Map.of("providerType", options.providerType()))
            .build();
    }

    protected GenerationResponseMetadata responseMetadata(String data, JsonNode root, String id,
        String model) {
        return GenerationResponseMetadata.builder()
            .id(id)
            .model(model)
            .timestamp(Instant.now())
            .body(parseBody(data))
            .metadata(Map.of("providerType", options.providerType()))
            .build();
    }

    protected ImageUsage tokenUsage(JsonNode usage, int imageCount) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return ImageUsage.builder().imageCount(imageCount).build();
        }
        return ImageUsage.builder()
            .inputTokens(firstInt(usage, "input_tokens", "prompt_tokens"))
            .outputTokens(firstInt(usage, "output_tokens", "completion_tokens"))
            .totalTokens(firstInt(usage, "total_tokens"))
            .imageCount(firstInt(usage, "image_count", "generated_images") != null
                ? firstInt(usage, "image_count", "generated_images") : imageCount)
            .raw(OBJECT_MAPPER.convertValue(usage, Object.class))
            .build();
    }

    protected String outputMediaType(String outputFormat) {
        if (!hasText(outputFormat)) {
            return "image/png";
        }
        return switch (outputFormat.toLowerCase(java.util.Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    protected Object parseBody(String data) {
        if (!hasText(data)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(data, Object.class);
        } catch (JsonProcessingException e) {
            return data;
        }
    }

    protected String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    protected Integer firstInt(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (var field : fields) {
            var value = node.path(field);
            if (value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }

    protected boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    protected String trimTrailingSlash(String value) {
        var result = value;
        while (result != null && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private Mono<String> errorBody(ClientResponse response) {
        return response.bodyToMono(String.class).defaultIfEmpty("");
    }

    private IllegalStateException requestFailed(ClientResponse response, String body) {
        return new IllegalStateException(options.providerType()
            + " image request failed: status=" + response.statusCode().value() + ", body="
            + body);
    }
}
