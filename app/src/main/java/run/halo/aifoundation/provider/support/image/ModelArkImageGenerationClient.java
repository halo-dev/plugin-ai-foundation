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

public class ModelArkImageGenerationClient extends AbstractJsonImageGenerationClient {

    public ModelArkImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    Map<String, Object> requestBody(GenerateImageRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            var images = request.getImages().stream()
                .map(this::imageSource)
                .toList();
            body.put("image", images.size() == 1 ? images.getFirst() : images);
        }
        putIfHasText(body, "size", request.getSize());
        putIfNotNull(body, "seed", request.getSeed());
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat() == ImageResponseFormat.BASE64
                ? "b64_json" : "url");
        }
        body.put("stream", false);
        putProviderOptions(body, request);
        return body;
    }

    @Override
    GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "ModelArk");
        var images = new ArrayList<GeneratedFile>();
        var outputFormat = String.valueOf(providerOptions(request).getOrDefault("output_format",
            "png"));
        var mediaType = outputMediaType(outputFormat);
        var dataNode = root.path("data");
        if (dataNode.isArray()) {
            for (var item : dataNode) {
                var metadata = new LinkedHashMap<String, Object>();
                putIfHasText(metadata, "size", textOrNull(item.path("size")));
                var url = textOrNull(item.path("url"));
                var base64 = textOrNull(item.path("b64_json"));
                if (hasText(base64)) {
                    images.add(GeneratedFile.builder()
                        .base64(base64)
                        .mediaType(mediaType)
                        .metadata(metadata)
                        .build());
                } else if (hasText(url)) {
                    images.add(GeneratedFile.builder()
                        .url(url)
                        .mediaType(mediaType)
                        .metadata(metadata)
                        .build());
                }
            }
        }
        return result(data, root, List.copyOf(images), tokenUsage(root.path("usage"),
            images.size()), List.of(), textOrNull(root.path("id")),
            textOrNull(root.path("model")));
    }
}
