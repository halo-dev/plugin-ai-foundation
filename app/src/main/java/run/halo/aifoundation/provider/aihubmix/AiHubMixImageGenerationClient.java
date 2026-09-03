package run.halo.aifoundation.provider.aihubmix;

import com.fasterxml.jackson.databind.JsonNode;
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
import run.halo.aifoundation.provider.support.JsonNodes;
import run.halo.aifoundation.provider.support.UriReferencePolicy;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** AIHubMix prediction client for explicitly routed synchronous image generation. */
public final class AiHubMixImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final UriReferencePolicy HTTP_IMAGE_REFERENCES =
        UriReferencePolicy.allowing("http://", "https://");
    private static final UriReferencePolicy DATA_REFERENCES =
        UriReferencePolicy.allowing("data:");
    private static final Set<String> OPTIONS = Set.of(
        "model_path", "input_fidelity", "moderation", "output_format", "background",
        "quality", "numberOfImages", "sampleCount", "safety_tolerance", "raw",
        "prompt_upsampling", "negative_prompt",
        "prompt_extend", "thinking_mode", "watermark", "color_palette",
        "sequential_image_generation", "stream", "response_format", "count_field");

    public AiHubMixImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        throw new UnsupportedOperationException("AIHubMix image endpoint requires a model path");
    }

    @Override
    protected String endpointPath(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        return "/models/" + modelPath(nativeOptions) + "/predictions";
    }

    @Override
    protected Map<String, Object> requestBody(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        validate(request, nativeOptions);
        var values = nativeOptions;
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported AIHubMix image option(s): "
                + String.join(", ", unknown));
        }
        var input = new LinkedHashMap<String, Object>();
        input.put("prompt", request.getPrompt());
        values.forEach((key, value) -> {
            if (!Set.of("model_path", "count_field").contains(key) && value != null) {
                input.put(key, value);
            }
        });
        if (request.getN() != null) {
            input.put(mappedField(values, "count_field", "n"), request.getN());
        }
        putIfHasText(input, "size", request.getSize());
        putIfHasText(input, "aspect_ratio", request.getAspectRatio());
        putIfNotNull(input, "seed", request.getSeed());
        if (request.getNegativePrompt() != null && !request.getNegativePrompt().isBlank()) {
            input.put("negative_prompt", request.getNegativePrompt());
        }
        putImages(input, request);
        return Map.of("input", Map.copyOf(input));
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var root = readTree(data, "AIHubMix");
        var images = new ArrayList<GeneratedFile>();
        collectImages(root.path("output"), nativeOptions, images);
        collectImages(root.path("data"), nativeOptions, images);
        if (images.isEmpty()) {
            collectImage(root, nativeOptions, images);
        }
        if (images.isEmpty() && isAsynchronousResponse(root)) {
            throw new IllegalStateException(
                "AIHubMix returned an asynchronous image task; this adapter supports only "
                    + "documented synchronous prediction results");
        }
        if (images.isEmpty()) {
            throw new IllegalStateException("AIHubMix image response contained no image output");
        }
        return result(data, root, List.copyOf(images),
            tokenUsage(root.path("usage"), images.size()), List.of(),
            firstText(root, "id", "prediction_id", "request_id"), options.model());
    }

    private boolean isAsynchronousResponse(JsonNode root) {
        if (hasText(textOrNull(root.path("taskId")))) {
            return true;
        }
        return hasText(textOrNull(root.path("polling_url")));
    }

    private void validate(GenerateImageRequest request, Map<String, Object> nativeOptions) {
        requirePrompt(request, "AIHubMix image prompt must not be blank");
        if (request.getMask() != null) {
            throw new IllegalArgumentException(
                "AIHubMix prediction image route does not document mask input");
        }
        validateImageCount(request.getN());
        validateStreamOption(nativeOptions.get("stream"));
        validateModelPath(modelPath(nativeOptions));
    }

    private void validateImageCount(Integer count) {
        if (count == null) {
            return;
        }
        if (count < 1) {
            throw new IllegalArgumentException("AIHubMix image n must be between 1 and 10");
        }
        if (count > 10) {
            throw new IllegalArgumentException("AIHubMix image n must be between 1 and 10");
        }
    }

    private void validateStreamOption(Object stream) {
        if (stream == null) {
            return;
        }
        if (!(stream instanceof Boolean enabled)) {
            throw new IllegalArgumentException(
                "AIHubMix synchronous prediction requires stream=false");
        }
        if (enabled) {
            throw new IllegalArgumentException(
                "AIHubMix synchronous prediction requires stream=false");
        }
    }

    private void validateModelPath(String path) {
        if (!path.contains("/")) {
            throw new IllegalArgumentException(
                "AIHubMix model_path must use the documented provider/model form");
        }
        if (path.startsWith("/")) {
            throw new IllegalArgumentException(
                "AIHubMix model_path must use the documented provider/model form");
        }
        if (path.endsWith("/")) {
            throw new IllegalArgumentException(
                "AIHubMix model_path must use the documented provider/model form");
        }
    }

    private void putImages(Map<String, Object> input, GenerateImageRequest request) {
        if (request.getImages() == null || request.getImages().isEmpty()) {
            return;
        }
        var images = request.getImages().stream().map(this::imageSource).toList();
        input.put("image", images.size() == 1 ? images.get(0) : images);
    }

    private String modelPath(Map<String, Object> nativeOptions) {
        var configured = nativeOptions.get("model_path");
        if (configured != null) {
            if (configured instanceof String path && !path.isBlank()) {
                return path;
            }
            throw new IllegalArgumentException("AIHubMix model_path must be a non-blank string");
        }
        throw new IllegalArgumentException(
            "AIHubMix image models require nativeOptions.model_path; "
                + "routing is configured by the administrator instead of inferred from model IDs");
    }

    private String mappedField(Map<String, Object> values, String option, String fallback) {
        var value = values.get(option);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String field && !field.isBlank()) {
            return field;
        }
        throw new IllegalArgumentException("AIHubMix " + option + " must be a non-blank string");
    }

    private void collectImages(JsonNode node, Map<String, Object> nativeOptions,
        List<GeneratedFile> images) {
        if (node.isArray()) {
            node.forEach(item -> collectImage(item, nativeOptions, images));
        } else {
            collectImage(node, nativeOptions, images);
        }
    }

    private void collectImage(JsonNode node, Map<String, Object> nativeOptions,
        List<GeneratedFile> images) {
        if (JsonNodes.isAbsent(node)) {
            return;
        }
        if (node.isTextual()) {
            addStringImage(node.asText(), configuredMediaType(nativeOptions), images);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (var field : List.of("images", "results", "data", "output")) {
            var nested = node.path(field);
            if (nested.isArray()) {
                collectImages(nested, nativeOptions, images);
            }
        }
        var mediaType = firstText(node, "media_type", "mime_type");
        if (!hasText(mediaType)) {
            mediaType = configuredMediaType(nativeOptions);
        }
        var url = firstText(node, "url", "image_url");
        if (hasText(url)) {
            images.add(GeneratedFile.url(url, mediaType));
            return;
        }
        var base64 = firstText(node, "b64_json", "base64", "image_base64");
        if (hasText(base64)) {
            images.add(GeneratedFile.base64(stripDataPrefix(base64), mediaType));
        }
    }

    private void addStringImage(String value, String mediaType, List<GeneratedFile> images) {
        if (!hasText(value)) {
            return;
        }
        if (HTTP_IMAGE_REFERENCES.allows(value)) {
            images.add(GeneratedFile.url(value, mediaType));
            return;
        }
        if (DATA_REFERENCES.allows(value)) {
            images.add(GeneratedFile.base64(stripDataPrefix(value), mediaType));
        }
    }

    private String stripDataPrefix(String value) {
        var comma = value.indexOf(',');
        if (!DATA_REFERENCES.allows(value)) {
            return value;
        }
        return comma >= 0 ? value.substring(comma + 1) : value;
    }

    private String configuredMediaType(Map<String, Object> nativeOptions) {
        var format = nativeOptions.get("output_format");
        return outputMediaType(format != null ? format.toString() : null);
    }

    private String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = textOrNull(node.path(field));
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
