package run.halo.aifoundation.provider.ernie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** Qianfan v2 image generation and URL-based multi-image editing adapter. */
public final class ErnieImageGenerationClient extends AbstractJsonImageGenerationClient {

    public ErnieImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    protected String endpointPath(GenerateImageRequest request) {
        return hasInputImages(request) ? "/images/edits" : endpointPath();
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
        if (hasInputImages(request)) {
            var images = request.getImages().stream().map(image -> image.getUrl()).toList();
            body.put("image", images.size() == 1 ? images.getFirst() : images);
        }
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "size", request.getSize());
        putIfHasText(body, "negative_prompt", request.getNegativePrompt());
        putIfNotNull(body, "seed", request.getSeed());
        return body;
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var root = readTree(data, "Qianfan");
        var images = new ArrayList<GeneratedFile>();
        for (var item : root.path("data")) {
            var url = textOrNull(item.path("url"));
            if (!hasText(url)) {
                continue;
            }
            var metadata = new LinkedHashMap<String, Object>();
            putIfHasText(metadata, "revisedPrompt", textOrNull(item.path("revised_prompt")));
            metadata.put("urlExpiresInSeconds", 24 * 60 * 60);
            images.add(GeneratedFile.builder()
                .url(url)
                .mediaType("image/png")
                .metadata(Map.copyOf(metadata))
                .build());
        }
        var warnings = new ArrayList<ImageGenerationWarning>();
        if (request.getResponseFormat() == ImageResponseFormat.BASE64) {
            warnings.add(ImageGenerationWarning.builder()
                .code("response-format-unsupported")
                .message("Qianfan image APIs return temporary URLs; base64 was not requested.")
                .providerMetadata(Map.of("providerType", "ernie"))
                .build());
        }
        return result(data, root, List.copyOf(images), tokenUsage(root.path("usage"), images.size()),
            List.copyOf(warnings), textOrNull(root.path("id")), textOrNull(root.path("model")));
    }

    private void validate(GenerateImageRequest request) {
        if (request.getMask() != null) {
            throw new IllegalArgumentException("Qianfan v2 image editing does not support masks");
        }
        if (request.getN() != null && (request.getN() < 1 || request.getN() > 4)) {
            throw new IllegalArgumentException("Qianfan image generation n must be between 1 and 4");
        }
        if (hasInputImages(request)) {
            for (var image : request.getImages()) {
                if (image == null || !image.isUrl()) {
                    throw new IllegalArgumentException(
                        "Qianfan image editing requires provider-accessible image URLs");
                }
            }
        }
    }

    private boolean hasInputImages(GenerateImageRequest request) {
        return request.getImages() != null && !request.getImages().isEmpty();
    }
}
