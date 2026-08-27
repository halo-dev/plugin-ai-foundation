package run.halo.aifoundation.provider.siliconflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** SiliconFlow native image client with explicit, model-owned field mappings. */
public final class SiliconFlowImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final String IMAGE_FIELD = "image_field";
    private static final String COUNT_FIELD = "count_field";
    private static final String SIZE_FIELD = "size_field";

    public SiliconFlowImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    public Map<String, Object> requestBody(GenerateImageRequest request) {
        validateRequest(request);
        var nativeOptions = nativeOptions(request);
        var body = forwardedOptions(nativeOptions);
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putMapped(body, nativeOptions, SIZE_FIELD, "image_size", request.getSize());
        putMapped(body, nativeOptions, COUNT_FIELD, "batch_size", request.getN());
        putIfHasText(body, "aspect_ratio", request.getAspectRatio());
        putIfNotNull(body, "seed", request.getSeed());
        putIfHasText(body, "negative_prompt", request.getNegativePrompt());
        putInputImages(body, nativeOptions, request);
        return Map.copyOf(body);
    }

    @Override
    public GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "SiliconFlow");
        var files = new ArrayList<GeneratedFile>();
        var mediaType = outputMediaType(outputFormat(request));
        for (var item : root.path("images")) {
            var url = textOrNull(item.path("url"));
            if (hasText(url)) {
                files.add(GeneratedFile.url(url, mediaType));
            }
        }
        var rawUsage = new LinkedHashMap<String, Object>();
        if (root.path("timings").isObject()) {
            rawUsage.put("timings", OBJECT_MAPPER.convertValue(root.path("timings"), Object.class));
        }
        if (root.path("seed").isNumber()) {
            rawUsage.put("seed", root.path("seed").asLong());
        }
        return result(data, root, List.copyOf(files), ImageUsage.builder()
                .imageCount(files.size())
                .raw(Map.copyOf(rawUsage))
                .build(),
            List.of(), null, options.model());
    }

    private void validateRequest(GenerateImageRequest request) {
        requirePrompt(request, "SiliconFlow image prompt must not be blank");
        if (request.getMask() != null) {
            throw new IllegalArgumentException("SiliconFlow image generation does not support masks");
        }
        if (request.getN() != null && request.getN() < 1) {
            throw new IllegalArgumentException("SiliconFlow image count must be positive");
        }
        if (request.getSeed() != null && request.getSeed() < 0) {
            throw new IllegalArgumentException("SiliconFlow image seed must not be negative");
        }
    }

    private Map<String, Object> nativeOptions(GenerateImageRequest request) {
        var values = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "siliconflow");
        return Map.copyOf(values);
    }

    private String outputFormat(GenerateImageRequest request) {
        var value = nativeOptions(request).get("output_format");
        return value != null ? value.toString() : null;
    }

    private LinkedHashMap<String, Object> forwardedOptions(Map<String, Object> values) {
        var result = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            if (!List.of(IMAGE_FIELD, COUNT_FIELD, SIZE_FIELD).contains(key) && value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    private void putInputImages(Map<String, Object> body, Map<String, Object> options,
        GenerateImageRequest request) {
        if (request.getImages() == null || request.getImages().isEmpty()) {
            return;
        }
        var images = request.getImages().stream().map(this::imageSource).toList();
        var value = images.size() == 1 ? images.getFirst() : images;
        putMapped(body, options, IMAGE_FIELD, "image", value);
    }

    private void putMapped(Map<String, Object> body, Map<String, Object> options,
        String mappingOption, String defaultField, Object value) {
        if (value == null) {
            return;
        }
        var field = options.get(mappingOption);
        if (field == null) {
            body.put(defaultField, value);
            return;
        }
        if (!(field instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("SiliconFlow " + mappingOption
                + " must be a non-blank field name");
        }
        body.put(text, value);
    }
}
