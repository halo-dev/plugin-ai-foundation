package run.halo.aifoundation.provider.support.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.media.GeneratedFile;

public class OpenRouterImageGenerationClient extends AbstractJsonImageGenerationClient {

    public OpenRouterImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images";
    }

    @Override
    Map<String, Object> requestBody(GenerateImageRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "size", request.getSize());
        putIfHasText(body, "aspect_ratio", request.getAspectRatio());
        putIfNotNull(body, "seed", request.getSeed());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            body.put("input_references", request.getImages().stream()
                .map(content -> Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageSource(content))
                ))
                .toList());
        }
        putProviderOptions(body, request);
        return body;
    }

    @Override
    GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "OpenRouter");
        var images = new ArrayList<GeneratedFile>();
        var outputFormat = textOrNull(root.path("output_format"));
        var providerOutputFormat = providerOptions(request).get("output_format");
        if (providerOutputFormat != null) {
            outputFormat = providerOutputFormat.toString();
        }
        var mediaType = outputMediaType(outputFormat);
        var dataNode = root.path("data");
        if (dataNode.isArray()) {
            for (var item : dataNode) {
                var base64 = textOrNull(item.path("b64_json"));
                var url = textOrNull(item.path("url"));
                var metadata = new LinkedHashMap<String, Object>();
                putIfHasText(metadata, "size", textOrNull(item.path("size")));
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
