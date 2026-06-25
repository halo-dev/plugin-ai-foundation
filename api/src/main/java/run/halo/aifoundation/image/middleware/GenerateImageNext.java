package run.halo.aifoundation.image.middleware;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;

/**
 * Next generation step in an image generation middleware chain.
 */
@FunctionalInterface
public interface GenerateImageNext {

    /**
     * Continues image generation with the provided request.
     *
     * @param request transformed image generation request
     * @return image generation result
     */
    Mono<GenerateImageResult> generate(GenerateImageRequest request);
}
