package run.halo.aifoundation.provider.ollama;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** Dedicated client for Ollama's experimental OpenAI-compatible image endpoint. */
public final class OllamaImageGenerationClient extends AbstractJsonImageGenerationClient {

    public OllamaImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    protected Map<String, Object> requestBody(GenerateImageRequest request) {
        requirePrompt(request, "Ollama image prompt must not be blank");
        validateResponseFormat(request.getResponseFormat());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            throw new IllegalArgumentException(
                "Ollama image editing is not documented by the experimental endpoint");
        }
        if (request.getMask() != null) {
            throw new IllegalArgumentException(
                "Ollama image masks are not documented by the experimental endpoint");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "size", request.getSize());
        body.put("response_format", "b64_json");
        return body;
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "Ollama");
        var images = new ArrayList<GeneratedFile>();
        var output = root.path("data");
        if (output.isArray()) {
            for (var item : output) {
                var base64 = textOrNull(item.path("b64_json"));
                if (hasText(base64)) {
                    images.add(GeneratedFile.base64(base64, "image/png"));
                }
            }
        }
        return result(data, root, List.copyOf(images),
            tokenUsage(root.path("usage"), images.size()), List.of(),
            null, options.model());
    }

    private void validateResponseFormat(ImageResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat == ImageResponseFormat.BASE64) {
            return;
        }
        throw new IllegalArgumentException(
            "Ollama image generation supports only the BASE64 response format");
    }
}
