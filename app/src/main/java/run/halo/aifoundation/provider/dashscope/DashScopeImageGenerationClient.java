package run.halo.aifoundation.provider.dashscope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

public final class DashScopeImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final String ENDPOINT_PATH =
        "/services/aigc/multimodal-generation/generation";

    public DashScopeImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    public Map<String, Object> requestBody(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());

        var content = new ArrayList<Map<String, Object>>();
        if (request.getImages() != null) {
            for (var image : request.getImages()) {
                content.add(Map.of("image", imageSource(image)));
            }
        }
        content.add(Map.of("text", request.getPrompt()));
        body.put("input", Map.of("messages", List.of(Map.of(
            "role", "user",
            "content", content
        ))));

        var parameters = new LinkedHashMap<String, Object>();
        putIfNotNull(parameters, "n", request.getN());
        putIfHasText(parameters, "size", dashScopeSize(request.getSize()));
        putIfNotNull(parameters, "seed", request.getSeed());
        putIfHasText(parameters, "negative_prompt", request.getNegativePrompt());
        if (!parameters.isEmpty()) {
            body.put("parameters", parameters);
        }
        return body;
    }

    @Override
    public GenerateImageResult imageResponse(String data, GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var root = readTree(data, "DashScope");
        var images = new ArrayList<GeneratedFile>();
        var choices = root.path("output").path("choices");
        if (choices.isArray()) {
            for (var choice : choices) {
                images.addAll(choiceImages(choice));
            }
        }
        return result(data, root, List.copyOf(images), tokenUsage(root.path("usage"),
            images.size()), List.of(), textOrNull(root.path("request_id")),
            textOrNull(root.path("model")));
    }

    private List<GeneratedFile> choiceImages(com.fasterxml.jackson.databind.JsonNode choice) {
        var content = choice.path("message").path("content");
        if (!content.isArray()) {
            return List.of();
        }
        var images = new ArrayList<GeneratedFile>();
        for (var part : content) {
            var image = textOrNull(part.path("image"));
            if (!hasText(image)) {
                continue;
            }
            images.add(GeneratedFile.url(image, "image/png"));
        }
        return List.copyOf(images);
    }

    @Override
    protected String endpointUrl(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        return new DashScopeEndpointResolver(options.baseUrl()).nativeBaseUrl() + ENDPOINT_PATH;
    }

    @Override
    protected String endpointPath() {
        return "/api/v1" + ENDPOINT_PATH;
    }

    private String dashScopeSize(String size) {
        if (!hasText(size)) {
            return null;
        }
        return size.replace('x', '*').replace('X', '*');
    }
}
