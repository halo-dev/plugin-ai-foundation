package run.halo.aifoundation.provider.support;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;

/**
 * Provider adapter used by the image generation runtime.
 */
public interface ProviderImageGenerationClient {

    Mono<GenerateImageResult> generateImage(GenerateImageRequest request);
}
