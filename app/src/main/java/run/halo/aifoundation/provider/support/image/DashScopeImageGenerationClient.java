package run.halo.aifoundation.provider.support.image;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.media.GeneratedFile;

public class DashScopeImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final String ENDPOINT_PATH =
        "/services/aigc/multimodal-generation/generation";

    public DashScopeImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointUrl() {
        var baseUrl = trimTrailingSlash(options.baseUrl());
        if (baseUrl.endsWith("/compatible-mode/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/compatible-mode/v1".length());
        }
        if (baseUrl.endsWith("/compatible-api/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/compatible-api/v1".length());
        }
        if (baseUrl.endsWith("/api/v1")) {
            return baseUrl + ENDPOINT_PATH;
        }
        return baseUrl + "/api/v1" + ENDPOINT_PATH;
    }

    @Override
    protected String endpointPath() {
        return "/api/v1" + ENDPOINT_PATH;
    }

    @Override
    Map<String, Object> requestBody(GenerateImageRequest request) {
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
        parameters.putAll(providerOptions(request));
        if (!parameters.isEmpty()) {
            body.put("parameters", parameters);
        }
        return body;
    }

    @Override
    GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "DashScope");
        var images = new ArrayList<GeneratedFile>();
        var choices = root.path("output").path("choices");
        if (choices.isArray()) {
            for (var choice : choices) {
                var content = choice.path("message").path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (var part : content) {
                    var image = textOrNull(part.path("image"));
                    if (hasText(image)) {
                        images.add(GeneratedFile.url(image, "image/png"));
                    }
                }
            }
        }
        return result(data, root, List.copyOf(images), tokenUsage(root.path("usage"),
            images.size()), List.of(), textOrNull(root.path("request_id")),
            textOrNull(root.path("model")));
    }

    private String dashScopeSize(String size) {
        if (!hasText(size)) {
            return null;
        }
        return size.replace('x', '*').replace('X', '*');
    }
}
