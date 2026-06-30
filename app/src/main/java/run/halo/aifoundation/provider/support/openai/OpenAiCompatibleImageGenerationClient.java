package run.halo.aifoundation.provider.support.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;

public class OpenAiCompatibleImageGenerationClient implements ProviderImageGenerationClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String IMAGES_GENERATIONS_PATH = "/images/generations";

    private final OpenAiCompatibleImageOptions options;
    private final WebClient webClient;

    public OpenAiCompatibleImageGenerationClient(OpenAiCompatibleImageOptions options,
        WebClient.Builder webClientBuilder) {
        this.options = options;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
        if (hasInputImages(request)) {
            return Mono.error(new IllegalArgumentException(
                "OpenAI-compatible image adapter currently supports text-to-image requests only"));
        }
        var body = requestBody(request);
        return webClient.method(HttpMethod.POST)
            .uri(URI.create(imagesGenerationsUrl()))
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
                    return errorBody(response).flatMap(error -> Mono.error(requestFailed(response,
                        error)));
                }
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(data -> imageResponse(data, request));
            });
    }

    Map<String, Object> requestBody(GenerateImageRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        if (request.getN() != null) {
            body.put("n", request.getN());
        }
        if (hasText(request.getSize())) {
            body.put("size", request.getSize());
        }
        if (request.getResponseFormat() != null) {
            body.put("response_format", responseFormat(request.getResponseFormat()));
        }
        var providerOptions = request.getProviderOptions() == null ? null
            : request.getProviderOptions().get(options.providerType());
        if (providerOptions != null && !providerOptions.isEmpty()) {
            body.putAll(providerOptions);
        }
        return body;
    }

    GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        try {
            var root = OBJECT_MAPPER.readTree(data);
            var images = images(root.path("data"));
            var warnings = warnings(root.path("data"));
            var responseMetadata = GenerationResponseMetadata.builder()
                .id(textOrNull(root.path("id")))
                .model(textOrNull(root.path("model")))
                .timestamp(Instant.now())
                .body(parseBody(data))
                .metadata(Map.of("providerType", options.providerType()))
                .build();
            return GenerateImageResult.builder()
                .images(images)
                .usage(usage(root.path("usage"), images.size()))
                .warnings(warnings)
                .responses(List.of(responseMetadata))
                .providerMetadata(Map.of("providerType", options.providerType()))
                .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse OpenAI-compatible image response", e);
        }
    }

    private List<GeneratedFile> images(JsonNode data) {
        if (!data.isArray() || data.isEmpty()) {
            return List.of();
        }
        var images = new ArrayList<GeneratedFile>();
        for (var item : data) {
            var url = textOrNull(item.path("url"));
            var base64 = textOrNull(item.path("b64_json"));
            var metadata = new LinkedHashMap<String, Object>();
            var revisedPrompt = textOrNull(item.path("revised_prompt"));
            if (hasText(revisedPrompt)) {
                metadata.put("revisedPrompt", revisedPrompt);
            }
            if (hasText(base64)) {
                images.add(GeneratedFile.builder()
                    .base64(base64)
                    .mediaType("image/png")
                    .metadata(metadata)
                    .build());
            } else if (hasText(url)) {
                images.add(GeneratedFile.builder()
                    .url(url)
                    .mediaType("image/png")
                    .metadata(metadata)
                    .build());
            }
        }
        return List.copyOf(images);
    }

    private List<ImageGenerationWarning> warnings(JsonNode data) {
        if (!data.isArray() || data.isEmpty()) {
            return List.of();
        }
        var warnings = new ArrayList<ImageGenerationWarning>();
        for (var item : data) {
            var revisedPrompt = textOrNull(item.path("revised_prompt"));
            if (hasText(revisedPrompt)) {
                warnings.add(ImageGenerationWarning.builder()
                    .code("prompt-revised")
                    .message("Provider revised the image generation prompt.")
                    .providerMetadata(Map.of("revisedPrompt", revisedPrompt))
                    .build());
            }
        }
        return List.copyOf(warnings);
    }

    private ImageUsage usage(JsonNode usage, int imageCount) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return ImageUsage.builder().imageCount(imageCount).build();
        }
        return ImageUsage.builder()
            .inputTokens(intOrNull(usage.path("input_tokens")))
            .outputTokens(intOrNull(usage.path("output_tokens")))
            .totalTokens(intOrNull(usage.path("total_tokens")))
            .imageCount(imageCount)
            .raw(OBJECT_MAPPER.convertValue(usage, Object.class))
            .build();
    }

    private Object parseBody(String data) throws JsonProcessingException {
        if (!hasText(data)) {
            return Map.of();
        }
        return OBJECT_MAPPER.readValue(data, Object.class);
    }

    private String imagesGenerationsUrl() {
        return trimTrailingSlash(options.baseUrl())
            + endpointPath(options.endpointPath(), IMAGES_GENERATIONS_PATH);
    }

    private String endpointPath(String configuredPath, String defaultPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultPath;
        }
        return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    private String responseFormat(ImageResponseFormat responseFormat) {
        return switch (responseFormat) {
            case URL -> "url";
            case BASE64 -> "b64_json";
        };
    }

    private Mono<String> errorBody(ClientResponse response) {
        return response.bodyToMono(String.class).defaultIfEmpty("");
    }

    private IllegalStateException requestFailed(ClientResponse response, String body) {
        return new IllegalStateException("OpenAI-compatible image request failed: status="
            + response.statusCode().value() + ", body=" + body);
    }

    private boolean hasInputImages(GenerateImageRequest request) {
        return request.getImages() != null && !request.getImages().isEmpty()
            || request.getMask() != null;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank() || !value.endsWith("/")) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }

    private String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
