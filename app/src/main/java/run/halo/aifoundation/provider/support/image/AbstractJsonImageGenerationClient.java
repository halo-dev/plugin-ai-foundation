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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

public abstract class AbstractJsonImageGenerationClient implements ProviderImageGenerationClient {

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
        return generateImage(request, null);
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request,
        ParameterMappingTarget target) {
        var url = endpointUrl(request);
        var body = mappedRequestBody(request, target);
        var diagnostics = ProviderDiagnostics.create(options.providerType(), "image");
        diagnostics.request(url, body, false);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(url))
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
            .bodyValue(body)
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response,
                        options.providerType(), "image", diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(data -> imageResponse(data, request));
            });
    }

    protected abstract Map<String, Object> requestBody(GenerateImageRequest request);

    private Map<String, Object> mappedRequestBody(GenerateImageRequest request,
        ParameterMappingTarget target) {
        return ImageParameterMappingMerger.merge(requestBody(request), target);
    }

    protected abstract GenerateImageResult imageResponse(String data,
        GenerateImageRequest request);

    protected String endpointUrl(GenerateImageRequest request) {
        return ProviderUris.withoutTrailingSlashes(options.baseUrl()) + endpointPath(request);
    }

    protected String endpointPath(GenerateImageRequest request) {
        return endpointPath();
    }

    protected abstract String endpointPath();

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

    protected void requirePrompt(GenerateImageRequest request, String message) {
        if (request == null) {
            throw new IllegalArgumentException(message);
        }
        if (hasText(request.getPrompt())) {
            return;
        }
        throw new IllegalArgumentException(message);
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
        if (JsonNodes.isAbsent(usage)) {
            return ImageUsage.builder().imageCount(imageCount).build();
        }
        var generatedImages = firstInt(usage, "image_count", "generated_images");
        if (generatedImages == null) {
            generatedImages = imageCount;
        }
        return ImageUsage.builder()
            .inputTokens(firstInt(usage, "input_tokens", "prompt_tokens"))
            .outputTokens(firstInt(usage, "output_tokens", "completion_tokens"))
            .totalTokens(firstInt(usage, "total_tokens"))
            .imageCount(generatedImages)
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
        if (node == null) {
            return null;
        }
        return node.isTextual() ? node.asText() : null;
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
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }

}
