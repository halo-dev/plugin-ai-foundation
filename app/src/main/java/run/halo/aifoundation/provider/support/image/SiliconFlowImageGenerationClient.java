package run.halo.aifoundation.provider.support.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.GeneratedFile;

public class SiliconFlowImageGenerationClient extends AbstractJsonImageGenerationClient {

    public SiliconFlowImageGenerationClient(ImageGenerationClientOptions options,
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
        putIfHasText(body, "image_size", request.getSize());
        putIfNotNull(body, "batch_size", request.getN());
        putIfNotNull(body, "seed", request.getSeed());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            body.put("image", imageSource(request.getImages().getFirst()));
            if (request.getImages().size() > 1) {
                body.put("image2", imageSource(request.getImages().get(1)));
            }
        }
        putProviderOptions(body, request);
        return body;
    }

    @Override
    GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "SiliconFlow");
        var images = new ArrayList<GeneratedFile>();
        var imageNodes = root.path("images");
        if (imageNodes.isArray()) {
            for (var item : imageNodes) {
                var url = textOrNull(item.path("url"));
                if (hasText(url)) {
                    images.add(GeneratedFile.url(url, "image/png"));
                }
            }
        }
        return result(data, root, List.copyOf(images),
            ImageUsage.builder()
                .imageCount(images.size())
                .raw(OBJECT_MAPPER.convertValue(root.path("timings"), Object.class))
                .build(),
            List.of(), textOrNull(root.path("id")), options.model());
    }
}
