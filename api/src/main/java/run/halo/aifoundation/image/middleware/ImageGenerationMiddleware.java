package run.halo.aifoundation.image.middleware;

import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;

/**
 * Provider-neutral middleware for image generation calls.
 *
 * <p>Middleware can transform a request, wrap provider execution, short-circuit with a result, or
 * short-circuit with an error. The default implementation applies
 * {@link #transformRequest(ImageGenerationContext)} and delegates to the next step.
 */
public interface ImageGenerationMiddleware {

    /**
     * Transforms an image generation request before the next middleware or model execution.
     *
     * @param context middleware context
     * @return transformed request
     */
    default Mono<GenerateImageRequest> transformRequest(ImageGenerationContext context) {
        return Mono.just(Objects.requireNonNull(context.request(), "request must not be null"));
    }

    /**
     * Wraps image generation execution.
     *
     * @param context middleware context
     * @param next next generation step
     * @return image generation result
     */
    default Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
        GenerateImageNext next) {
        return transformRequest(context).flatMap(next::generate);
    }
}
