package run.halo.aifoundation.provider.support;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;

/**
 * Provider adapter used by the image generation runtime.
 */
public interface ProviderImageGenerationClient {

    Mono<GenerateImageResult> generateImage(GenerateImageRequest request);

    default Mono<GenerateImageResult> generateImage(GenerateImageRequest request,
        ParameterMappingTarget target) {
        return generateImage(request);
    }
}
