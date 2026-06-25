package run.halo.aifoundation.image;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.ModelCapabilities;

/**
 * Provider-neutral Java SDK for non-streaming image generation.
 */
public interface ImageGenerationModel {

    /**
     * Generates images from a prompt using default request settings.
     *
     * @param prompt text prompt
     * @return asynchronous image generation result
     */
    default Mono<GenerateImageResult> generateImage(String prompt) {
        return generateImage(GenerateImageRequest.builder()
            .prompt(prompt)
            .build());
    }

    /**
     * Generates images from a full provider-neutral request.
     *
     * <p>Implementations validate the request structure, media resources, and resolved model
     * capabilities before invoking the provider.
     *
     * @param request image generation request
     * @return asynchronous image generation result
     */
    Mono<GenerateImageResult> generateImage(GenerateImageRequest request);

    /**
     * Returns read-only capability metadata for this resolved model.
     *
     * <p>Implementations should be conservative: unknown capabilities should remain unknown and
     * required unknown capabilities are treated as unsupported during invocation.
     */
    default ModelCapabilities capabilities() {
        return ModelCapabilities.empty();
    }

    /**
     * Creates a model object that fails with an unsupported-capability error when invoked.
     *
     * <p>This is used internally when a configured model has an image-generation model type but
     * the provider adapter cannot safely serve image generation.
     *
     * @param modelName Halo model resource name
     * @param providerName Halo provider resource name
     * @param providerType provider type identifier
     * @return unsupported image generation model wrapper
     */
    static ImageGenerationModel unsupported(String modelName, String providerName,
        String providerType) {
        return new UnsupportedImageGenerationModel(modelName, providerName, providerType);
    }
}
