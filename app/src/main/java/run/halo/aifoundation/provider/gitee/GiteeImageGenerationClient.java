package run.halo.aifoundation.provider.gitee;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ProviderRequestOptions;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;

/** Gitee AI image generation adapter with provider-native reference images and controls. */
public final class GiteeImageGenerationClient extends AbstractJsonImageGenerationClient {

    public GiteeImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    protected Map<String, Object> requestBody(GenerateImageRequest request) {
        validate(request);
        var body = new LinkedHashMap<String, Object>();
        var providerOptions = ProviderRequestOptions.get(
            request.getProviderOptions(), "gitee-moark");
        ProviderRequestOptions.copyNonNullValues(body, providerOptions);
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putImages(body, request.getImages());
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "size", request.getSize());
        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat() == ImageResponseFormat.BASE64
                ? "b64_json" : "url");
        }
        return body;
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request) {
        var root = readTree(data, "Gitee AI");
        var images = new ArrayList<GeneratedFile>();
        var warnings = new ArrayList<ImageGenerationWarning>();
        var mediaType = outputMediaType(outputFormat(request));
        for (var item : root.path("data")) {
            var url = textOrNull(item.path("url"));
            var base64 = textOrNull(item.path("b64_json"));
            var revisedPrompt = textOrNull(item.path("revised_prompt"));
            var metadata = new LinkedHashMap<String, Object>();
            putIfHasText(metadata, "revisedPrompt", revisedPrompt);
            if (hasText(base64)) {
                images.add(GeneratedFile.builder().base64(base64).mediaType(mediaType)
                    .metadata(Map.copyOf(metadata)).build());
            } else if (hasText(url)) {
                images.add(GeneratedFile.builder().url(url).mediaType(mediaType)
                    .metadata(Map.copyOf(metadata)).build());
            }
            if (hasText(revisedPrompt)) {
                warnings.add(ImageGenerationWarning.builder()
                    .code("prompt-revised")
                    .message("Gitee AI revised the image generation prompt.")
                    .providerMetadata(Map.of("revisedPrompt", revisedPrompt))
                    .build());
            }
        }
        return result(data, root, List.copyOf(images), tokenUsage(root.path("usage"), images.size()),
            List.copyOf(warnings), textOrNull(root.path("id")), textOrNull(root.path("model")));
    }

    private void putImages(Map<String, Object> body, List<DataContent> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        var sources = images.stream().map(this::giteeImageSource).toList();
        if (sources.size() == 1) {
            body.put("image", sources.getFirst());
        } else {
            body.put("images", sources);
        }
    }

    private String giteeImageSource(DataContent image) {
        if (image == null || image.isUrl() == image.isData()) {
            throw new IllegalArgumentException(
                "Gitee AI reference image must set exactly one URL or base64 value");
        }
        return image.isUrl() ? image.getUrl() : image.getData();
    }

    private String outputFormat(GenerateImageRequest request) {
        var providerOptions = ProviderRequestOptions.orEmpty(
            request.getProviderOptions(), "gitee-moark");
        var value = providerOptions.get("output_format");
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private void validate(GenerateImageRequest request) {
        requirePrompt(request, "Gitee AI image prompt must not be blank");
        if (request.getPrompt().length() > 2000) {
            throw new IllegalArgumentException(
                "Gitee AI image prompt must not exceed 2000 characters");
        }
        if (request.getMask() != null) {
            throw new IllegalArgumentException(
                "Gitee AI JSON image generation does not support masks");
        }
        if (request.getN() != null && (request.getN() < 1 || request.getN() > 4)) {
            throw new IllegalArgumentException("Gitee AI image n must be between 1 and 4");
        }
        var outputFormat = outputFormat(request);
        if (hasText(outputFormat) && !List.of("png", "jpeg", "webp")
            .contains(outputFormat.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                "Gitee AI output_format must be png, jpeg, or webp");
        }
    }
}
