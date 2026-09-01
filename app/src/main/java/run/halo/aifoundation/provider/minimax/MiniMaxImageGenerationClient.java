package run.halo.aifoundation.provider.minimax;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.provider.transport.ProviderHttpException;

/** MiniMax native image generation and subject-reference adapter. */
public final class MiniMaxImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final int MAX_PROMPT_LENGTH = 1500;
    private static final List<String> ASPECT_RATIOS = List.of(
        "1:1", "16:9", "4:3", "3:2", "2:3", "3:4", "9:16", "21:9");

    public MiniMaxImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/v1/image_generation";
    }

    @Override
    protected Map<String, Object> requestBody(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        validate(request);
        var body = new LinkedHashMap<String, Object>();
        nativeOptions.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (value == null) {
                return;
            }
            body.put(key, value);
        });
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfHasText(body, "aspect_ratio", request.getAspectRatio());
        var dimensions = parseDimensions(request.getSize());
        if (dimensions != null) {
            body.put("width", dimensions.width());
            body.put("height", dimensions.height());
        }
        putIfNotNull(body, "seed", request.getSeed());
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "negative_prompt", request.getNegativePrompt());
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat() == ImageResponseFormat.BASE64
                ? "base64" : "url");
        }
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            body.put("subject_reference", List.of(Map.of(
                "type", "character",
                "image_file", imageSource(request.getImages().getFirst()))));
        }
        return body;
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var root = readTree(data, "MiniMax");
        var status = root.path("base_resp").path("status_code");
        if (status.isNumber() && status.asInt() != 0) {
            throw new ProviderHttpException("minimax", "image", 200, data);
        }
        var images = new ArrayList<GeneratedFile>();
        var imageUrls = root.path("data").path("image_urls");
        if (imageUrls.isArray()) {
            for (var item : imageUrls) {
                var url = textOrNull(item);
                if (hasText(url)) {
                    images.add(GeneratedFile.url(url, null));
                }
            }
        }
        var imageBase64 = root.path("data").path("image_base64");
        if (imageBase64.isArray()) {
            for (var item : imageBase64) {
                var base64 = textOrNull(item);
                if (hasText(base64)) {
                    images.add(GeneratedFile.base64(base64, null));
                }
            }
        }
        var warnings = new ArrayList<ImageGenerationWarning>();
        var failedCount = nonNegativeInteger(root.path("metadata").path("failed_count"));
        if (failedCount > 0) {
            warnings.add(ImageGenerationWarning.builder()
                .code("partial-generation")
                .message("MiniMax failed to generate " + failedCount
                    + " requested image(s).")
                .providerMetadata(Map.of("failedCount", failedCount))
                .build());
        }
        var usage = ImageUsage.builder()
            .imageCount(images.size())
            .raw(OBJECT_MAPPER.convertValue(root.path("metadata"), Object.class))
            .build();
        return result(data, root, List.copyOf(images), usage, List.copyOf(warnings),
            textOrNull(root.path("id")), options.model());
    }

    private int nonNegativeInteger(JsonNode node) {
        if (node.isIntegralNumber()) {
            return Math.max(0, node.asInt());
        }
        if (!node.isTextual()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(node.textValue()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void validate(GenerateImageRequest request) {
        requirePrompt(request, "MiniMax image prompt must not be blank");
        if (request.getPrompt().length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException(
                "MiniMax image prompt must not exceed 1500 characters");
        }
        if (request.getMask() != null) {
            throw new IllegalArgumentException("MiniMax image generation does not support masks");
        }
        if (request.getN() != null) {
            validateImageCount(request.getN());
        }
        if (request.getImages() != null && request.getImages().size() > 1) {
            throw new IllegalArgumentException(
                "MiniMax subject_reference accepts exactly one reference image");
        }
        if (hasText(request.getSize()) && hasText(request.getAspectRatio())) {
            throw new IllegalArgumentException(
                "MiniMax image size and aspectRatio are mutually exclusive");
        }
        if (hasText(request.getAspectRatio())
            && !ASPECT_RATIOS.contains(request.getAspectRatio().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                "Unsupported MiniMax image aspect ratio: " + request.getAspectRatio());
        }
        if (hasText(request.getSize())) {
            var dimensions = parseDimensions(request.getSize());
            if (dimensions == null) {
                throw new IllegalArgumentException(
                    "MiniMax image size must use WIDTHxHEIGHT format");
            }
            validateDimension(dimensions.width(), "width");
            validateDimension(dimensions.height(), "height");
        }
    }

    private void validateImageCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("MiniMax image n must be between 1 and 9");
        }
        if (count > 9) {
            throw new IllegalArgumentException("MiniMax image n must be between 1 and 9");
        }
    }

    private void validateDimension(int value, String field) {
        if (value < 512) {
            throw invalidDimension(field);
        }
        if (value > 2048) {
            throw invalidDimension(field);
        }
        if (value % 8 != 0) {
            throw invalidDimension(field);
        }
    }

    private IllegalArgumentException invalidDimension(String field) {
        return new IllegalArgumentException(
            "MiniMax image " + field + " must be 512-2048 pixels and divisible by 8");
    }

    private Dimensions parseDimensions(String size) {
        if (!hasText(size)) {
            return null;
        }
        var parts = size.toLowerCase(Locale.ROOT).split("x", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new Dimensions(Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Dimensions(int width, int height) {
    }
}
