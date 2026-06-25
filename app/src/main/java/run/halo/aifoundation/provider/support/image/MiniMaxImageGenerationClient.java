package run.halo.aifoundation.provider.support.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.GeneratedFile;

public class MiniMaxImageGenerationClient extends AbstractJsonImageGenerationClient {

    public MiniMaxImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/image_generation";
    }

    @Override
    Map<String, Object> requestBody(GenerateImageRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfHasText(body, "aspect_ratio", request.getAspectRatio());
        var dimensions = parseDimensions(request.getSize());
        if (dimensions != null && !body.containsKey("aspect_ratio")) {
            body.put("width", dimensions.width());
            body.put("height", dimensions.height());
        }
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat() == ImageResponseFormat.BASE64
                ? "base64" : "url");
        }
        putIfNotNull(body, "seed", request.getSeed());
        putIfNotNull(body, "n", request.getN());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            body.put("subject_reference", request.getImages().stream()
                .map(image -> Map.of(
                    "type", "character",
                    "image_file", imageSource(image)
                ))
                .toList());
        }
        putProviderOptions(body, request);
        return body;
    }

    @Override
    GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "MiniMax");
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
        return result(data, root, List.copyOf(images), ImageUsageBuilder.imageCount(images.size(),
            OBJECT_MAPPER.convertValue(root.path("metadata"), Object.class)), List.of(),
            textOrNull(root.path("id")), options.model());
    }

    private Dimensions parseDimensions(String size) {
        if (!hasText(size)) {
            return null;
        }
        var separator = size.toLowerCase(java.util.Locale.ROOT).indexOf('x');
        if (separator < 0) {
            return null;
        }
        try {
            var width = Integer.parseInt(size.substring(0, separator).trim());
            var height = Integer.parseInt(size.substring(separator + 1).trim());
            return new Dimensions(width, height);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Dimensions(int width, int height) {
    }

    private static final class ImageUsageBuilder {
        private static run.halo.aifoundation.image.ImageUsage imageCount(int count, Object raw) {
            return run.halo.aifoundation.image.ImageUsage.builder()
                .imageCount(count)
                .raw(raw)
                .build();
        }
    }
}
