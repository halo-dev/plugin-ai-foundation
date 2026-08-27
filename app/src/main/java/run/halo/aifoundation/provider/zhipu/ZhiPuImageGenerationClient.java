package run.halo.aifoundation.provider.zhipu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** Native synchronous BigModel image client. */
public final class ZhiPuImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final Set<String> OPTIONS = Set.of(
        "quality", "watermark_enabled", "user_id");

    public ZhiPuImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    protected Map<String, Object> requestBody(GenerateImageRequest request) {
        validateRequest(request);
        var nativeOptions = nativeOptions(request);
        validateNativeOptions(nativeOptions);
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfHasText(body, "size", request.getSize());
        nativeOptions.forEach((key, value) -> {
            if (value != null) {
                body.put(key, value);
            }
        });
        return body;
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "Zhipu");
        var images = new ArrayList<GeneratedFile>();
        for (var item : root.path("data")) {
            var url = textOrNull(item.path("url"));
            if (hasText(url)) {
                images.add(GeneratedFile.url(url, "image/png"));
            }
        }
        var raw = new LinkedHashMap<String, Object>();
        if (root.path("created").isNumber()) {
            raw.put("created", root.path("created").asLong());
        }
        if (root.path("content_filter").isArray()) {
            raw.put("content_filter",
                OBJECT_MAPPER.convertValue(root.path("content_filter"), Object.class));
        }
        return result(data, root, List.copyOf(images), ImageUsage.builder()
                .imageCount(images.size()).raw(Map.copyOf(raw)).build(),
            List.of(), null, options.model());
    }

    private void validateRequest(GenerateImageRequest request) {
        requirePrompt(request, "Zhipu image prompt must not be blank");
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            throw new IllegalArgumentException(
                "Zhipu synchronous image generation does not document input images");
        }
        if (request.getMask() != null) {
            throw new IllegalArgumentException(
                "Zhipu synchronous image generation does not document masks");
        }
        if (request.getN() != null && request.getN() != 1) {
            throw new IllegalArgumentException(
                "Zhipu synchronous image generation returns exactly one image");
        }
        if (hasUndocumentedControls(request)) {
            throw new IllegalArgumentException(
                "Zhipu image API does not document aspect ratio, negative prompt, seed, "
                    + "or response format controls");
        }
        validateSize(request.getSize());
    }

    private boolean hasUndocumentedControls(GenerateImageRequest request) {
        if (request.getAspectRatio() != null) {
            return true;
        }
        if (request.getNegativePrompt() != null) {
            return true;
        }
        if (request.getSeed() != null) {
            return true;
        }
        return request.getResponseFormat() != null;
    }

    private Map<String, Object> nativeOptions(GenerateImageRequest request) {
        var values = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "zhipuai");
        if (values.isEmpty()) {
            return Map.of();
        }
        var unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(OPTIONS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Zhipu image option(s): "
                + String.join(", ", unknown));
        }
        return Map.copyOf(values);
    }

    private void validateNativeOptions(Map<String, Object> values) {
        var quality = string(values.get("quality"), "quality");
        if (quality != null && !Set.of("hd", "standard").contains(quality)) {
            throw new IllegalArgumentException(
                "Zhipu image quality must be 'hd' or 'standard'");
        }
        if (values.get("watermark_enabled") != null
            && !(values.get("watermark_enabled") instanceof Boolean)) {
            throw new IllegalArgumentException("Zhipu watermark_enabled must be boolean");
        }
        var userId = string(values.get("user_id"), "user_id");
        if (userId != null && (userId.length() < 6 || userId.length() > 128)) {
            throw new IllegalArgumentException(
                "Zhipu image user_id length must be between 6 and 128");
        }
    }

    private void validateSize(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        var parts = value.toLowerCase(java.util.Locale.ROOT).split("x", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Zhipu image size must use WIDTHxHEIGHT");
        }
        try {
            var width = Integer.parseInt(parts[0]);
            var height = Integer.parseInt(parts[1]);
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Zhipu image dimensions must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Zhipu image size must use WIDTHxHEIGHT", e);
        }
    }

    private String string(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("Zhipu image " + field + " must be a string");
    }
}
