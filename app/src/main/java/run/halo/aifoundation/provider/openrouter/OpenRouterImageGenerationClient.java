package run.halo.aifoundation.provider.openrouter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** OpenRouter's dedicated Image API, independent from Chat Completions image output. */
public final class OpenRouterImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final Set<String> OPTIONS = Set.of(
        "resolution", "quality", "output_format", "background", "output_compression",
        "provider");

    public OpenRouterImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images";
    }

    @Override
    public Map<String, Object> requestBody(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        validate(request);
        var body = new LinkedHashMap<String, Object>();
        if (!nativeOptions.isEmpty()) {
            var unknown = new LinkedHashSet<>(nativeOptions.keySet());
            unknown.removeAll(OPTIONS);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Unsupported OpenRouter image option(s): "
                    + String.join(", ", unknown));
            }
            copyNonNullValues(body, nativeOptions);
            OpenRouterRoutingOptions.validate(nativeOptions.get("provider"), "image");
        }
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "size", request.getSize());
        putIfHasText(body, "aspect_ratio", request.getAspectRatio());
        putIfNotNull(body, "seed", request.getSeed());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            body.put("input_references", request.getImages().stream()
                .map(content -> Map.of("type", "image_url",
                    "image_url", Map.of("url", imageSource(content))))
                .toList());
        }
        return body;
    }

    @Override
    public GenerateImageResult imageResponse(String data, GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var root = readTree(data, "OpenRouter");
        var images = new ArrayList<GeneratedFile>();
        for (var item : root.path("data")) {
            var base64 = textOrNull(item.path("b64_json"));
            if (!hasText(base64)) {
                continue;
            }
            var mediaType = textOrNull(item.path("media_type"));
            if (!hasText(mediaType)) {
                mediaType = configuredMediaType(nativeOptions);
            }
            var metadata = new LinkedHashMap<String, Object>();
            putIfHasText(metadata, "revisedPrompt", textOrNull(item.path("revised_prompt")));
            images.add(GeneratedFile.builder()
                .base64(base64)
                .mediaType(mediaType)
                .metadata(Map.copyOf(metadata))
                .build());
        }
        var warnings = new ArrayList<ImageGenerationWarning>();
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
            warnings.add(ImageGenerationWarning.builder()
                .code("negative-prompt-unsupported")
                .message("OpenRouter's Image API does not define a negative prompt field.")
                .providerMetadata(Map.of("providerType", "openrouter"))
                .build());
        }
        return result(data, root, List.copyOf(images), tokenUsage(root.path("usage"), images.size()),
            List.copyOf(warnings), textOrNull(root.path("id")),
            hasText(textOrNull(root.path("model"))) ? textOrNull(root.path("model")) : options.model());
    }

    private void validate(GenerateImageRequest request) {
        requirePrompt(request, "OpenRouter image prompt must not be blank");
        if (request.getMask() != null) {
            throw new IllegalArgumentException("OpenRouter's Image API does not support masks");
        }
        if (request.getN() != null && (request.getN() < 1 || request.getN() > 10)) {
            throw new IllegalArgumentException("OpenRouter image n must be between 1 and 10");
        }
        if (hasText(request.getSize()) && hasText(request.getAspectRatio())
            && request.getSize().contains("x")) {
            throw new IllegalArgumentException(
                "OpenRouter explicit pixel size cannot be combined with aspectRatio");
        }
    }

    private String configuredMediaType(Map<String, Object> nativeOptions) {
        var format = outputFormat(nativeOptions);
        return switch (format) {
            case "jpeg", "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "image/png";
        };
    }

    private String outputFormat(Map<String, Object> values) {
        var value = values.get("output_format");
        if (value == null) {
            return "png";
        }
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private void copyNonNullValues(Map<String, Object> target,
        Map<String, Object> nativeOptions) {
        nativeOptions.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (value == null) {
                return;
            }
            target.put(key, value);
        });
    }
}
