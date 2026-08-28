package run.halo.aifoundation.provider.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;
import run.halo.aifoundation.provider.support.ProviderUris;
import run.halo.aifoundation.provider.support.image.AbstractJsonImageGenerationClient;
import run.halo.aifoundation.provider.support.image.ImageGenerationClientOptions;
import run.halo.aifoundation.provider.transport.ProviderDiagnostics;
import run.halo.aifoundation.provider.transport.ProviderHttpResponseSupport;

/** OpenAI image generation and multipart editing adapter. */
public final class OpenAiImageGenerationClient extends AbstractJsonImageGenerationClient {

    private static final Set<String> GENERATION_OPTIONS = Set.of(
        "quality", "background", "output_format", "output_compression", "moderation",
        "style", "user");
    private static final Set<String> EDIT_OPTIONS = Set.of(
        "quality", "background", "output_format", "output_compression", "input_fidelity",
        "user");
    private static final Set<String> EDIT_IMAGE_TYPES = Set.of(
        "image/png", "image/jpeg", "image/webp");

    private final WebClient editWebClient;

    public OpenAiImageGenerationClient(ImageGenerationClientOptions options,
        WebClient.Builder webClientBuilder) {
        super(options, webClientBuilder);
        this.editWebClient = webClientBuilder.build();
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request,
        ParameterMappingTarget target) {
        return generateImage(request, target, Map.of());
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request,
        ParameterMappingTarget target, Map<String, Object> nativeOptions) {
        if (!isEditRequest(request)) {
            return super.generateImage(request, target, nativeOptions);
        }
        return editImage(request, target, nativeOptions);
    }

    @Override
    protected String endpointPath() {
        return "/images/generations";
    }

    @Override
    protected Map<String, Object> requestBody(GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        validateCommon(request);
        return requestFields(request, nativeOptions, GENERATION_OPTIONS);
    }

    private Map<String, Object> requestFields(GenerateImageRequest request,
        Map<String, Object> nativeOptions, Set<String> allowedOptions) {
        var body = new LinkedHashMap<String, Object>();
        var unknown = new LinkedHashSet<>(nativeOptions.keySet());
        unknown.removeAll(allowedOptions);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported OpenAI image option(s): "
                + String.join(", ", unknown));
        }
        nativeOptions.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            body.put(key, value);
        });
        body.put("model", options.model());
        body.put("prompt", request.getPrompt());
        putIfNotNull(body, "n", request.getN());
        putIfHasText(body, "size", request.getSize());
        if (request.getResponseFormat() != null) {
            body.put("response_format", switch (request.getResponseFormat()) {
                case URL -> "url";
                case BASE64 -> "b64_json";
            });
        }
        return body;
    }

    private Mono<GenerateImageResult> editImage(GenerateImageRequest request,
        ParameterMappingTarget target, Map<String, Object> nativeOptions) {
        validateEdit(request);
        var fields = mappedEditFields(request, target, nativeOptions);
        var multipart = multipartBody(fields, request);
        var url = ProviderUris.withoutTrailingSlashes(options.baseUrl()) + "/images/edits";
        var diagnostics = ProviderDiagnostics.create(options.providerType(), "image-edit");
        diagnostics.request(url, editDiagnostics(fields, request), false);
        return editWebClient.post()
            .uri(url)
            .headers(headers -> {
                if (hasText(options.apiKey())) {
                    headers.setBearerAuth(options.apiKey());
                }
                options.customHeaders().forEach(headers::set);
                if (request.getHeaders() != null) {
                    request.getHeaders().forEach(headers::set);
                }
            })
            .body(BodyInserters.fromMultipartData(multipart.build()))
            .exchangeToMono(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return ProviderHttpResponseSupport.errorMono(response,
                        options.providerType(), "image-edit", diagnostics);
                }
                return ProviderHttpResponseSupport.body(response, diagnostics)
                    .map(data -> imageResponse(data, request, nativeOptions));
            });
    }

    private Map<String, Object> mappedEditFields(GenerateImageRequest request,
        ParameterMappingTarget target, Map<String, Object> nativeOptions) {
        var fields = requestFields(request, nativeOptions, EDIT_OPTIONS);
        if (target == null) {
            return fields;
        }
        for (var field : List.of("n", "size", "response_format")) {
            fields.remove(field);
        }
        fields.putAll(target.root());
        if (!target.parameters().isEmpty()) {
            throw new IllegalArgumentException(
                "OpenAI image edit parameter mappings must target root fields");
        }
        return fields;
    }

    private MultipartBodyBuilder multipartBody(Map<String, Object> fields,
        GenerateImageRequest request) {
        var multipart = new MultipartBodyBuilder();
        fields.forEach((name, value) -> multipart.part(name, value.toString()));
        var images = request.getImages();
        for (int index = 0; index < images.size(); index++) {
            addFilePart(multipart, "image", images.get(index), "image-" + (index + 1));
        }
        if (request.getMask() != null) {
            addFilePart(multipart, "mask", request.getMask(), "mask");
        }
        return multipart;
    }

    private void addFilePart(MultipartBodyBuilder multipart, String name, DataContent content,
        String fallbackName) {
        var filename = hasText(content.getFilename())
            ? content.getFilename() : fallbackName + extension(content.getMediaType());
        var resource = new NamedByteArrayResource(content.decodedData(), filename);
        multipart.part(name, resource)
            .contentType(MediaType.parseMediaType(content.getMediaType()));
    }

    private Map<String, Object> editDiagnostics(Map<String, Object> fields,
        GenerateImageRequest request) {
        var summary = new LinkedHashMap<>(fields);
        summary.put("imageCount", request.getImages().size());
        summary.put("mask", request.getMask() != null);
        return Map.copyOf(summary);
    }

    @Override
    protected GenerateImageResult imageResponse(String data, GenerateImageRequest request,
        Map<String, Object> nativeOptions) {
        var root = readTree(data, "OpenAI");
        var images = new ArrayList<GeneratedFile>();
        var warnings = new ArrayList<ImageGenerationWarning>();
        var mediaType = configuredMediaType(nativeOptions);
        for (var item : root.path("data")) {
            var metadata = new LinkedHashMap<String, Object>();
            var revised = textOrNull(item.path("revised_prompt"));
            if (hasText(revised)) {
                metadata.put("revisedPrompt", revised);
                warnings.add(ImageGenerationWarning.builder().code("prompt-revised")
                    .message("OpenAI revised the image generation prompt.")
                    .providerMetadata(Map.of("revisedPrompt", revised)).build());
            }
            var base64 = textOrNull(item.path("b64_json"));
            var url = textOrNull(item.path("url"));
            if (hasText(base64)) {
                images.add(GeneratedFile.builder().base64(base64).mediaType(mediaType)
                    .metadata(Map.copyOf(metadata)).build());
            } else if (hasText(url)) {
                images.add(GeneratedFile.builder().url(url).mediaType(mediaType)
                    .metadata(Map.copyOf(metadata)).build());
            }
        }
        return result(data, root, List.copyOf(images),
            tokenUsage(root.path("usage"), images.size()), List.copyOf(warnings),
            textOrNull(root.path("id")),
            hasText(textOrNull(root.path("model")))
                ? textOrNull(root.path("model")) : options.model());
    }

    private void validateCommon(GenerateImageRequest request) {
        requirePrompt(request, "OpenAI image prompt must not be blank");
        if (request.getN() == null) {
            return;
        }
        if (request.getN() < 1) {
            throw new IllegalArgumentException("OpenAI image n must be between 1 and 10");
        }
        if (request.getN() > 10) {
            throw new IllegalArgumentException("OpenAI image n must be between 1 and 10");
        }
    }

    private void validateEdit(GenerateImageRequest request) {
        validateCommon(request);
        if (request.getImages() == null) {
            throw new IllegalArgumentException(
                "OpenAI image editing requires at least one input image");
        }
        if (request.getImages().isEmpty()) {
            throw new IllegalArgumentException(
                "OpenAI image editing requires at least one input image");
        }
        for (var image : request.getImages()) {
            validateEditFile(image, "OpenAI edit image");
        }
        if (request.getMask() == null) {
            return;
        }
        validateEditFile(request.getMask(), "OpenAI edit mask");
        if (!"image/png".equalsIgnoreCase(request.getMask().getMediaType())) {
            throw new IllegalArgumentException("OpenAI edit mask must be a PNG image");
        }
    }

    private void validateEditFile(DataContent content, String label) {
        if (content == null) {
            throw new IllegalArgumentException(label + " must use caller-provided image data");
        }
        if (!content.isData()) {
            throw new IllegalArgumentException(label + " must use caller-provided image data");
        }
        var mediaType = content.getMediaType().toLowerCase(Locale.ROOT);
        if (!EDIT_IMAGE_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException(label + " must be PNG, JPEG, or WebP");
        }
    }

    private boolean isEditRequest(GenerateImageRequest request) {
        if (request.getMask() != null) {
            return true;
        }
        if (request.getImages() == null) {
            return false;
        }
        return !request.getImages().isEmpty();
    }

    private String extension(String mediaType) {
        return switch (mediaType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private String configuredMediaType(Map<String, Object> nativeOptions) {
        var value = nativeOptions.get("output_format");
        var format = value != null ? value.toString().toLowerCase(Locale.ROOT) : "png";
        return outputMediaType(format);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] data, String filename) {
            super(data);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
