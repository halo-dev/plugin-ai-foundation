package run.halo.aifoundation.image.middleware;

import java.util.List;
import run.halo.aifoundation.image.ImageGenerationModel;

/**
 * Extension point for image model wrappers that must control where middleware is inserted.
 *
 * <p>Most callers should use {@link ImageGenerationMiddlewares#wrap(ImageGenerationModel, List)}.
 * SDK-managed wrappers use this hook to preserve behavior such as caller audit when middleware
 * short-circuits before reaching the provider runtime.
 */
public interface ImageGenerationMiddlewareAware {

    /**
     * Wraps this model with image generation middleware while preserving wrapper-specific behavior.
     *
     * @param middleware model-level middleware
     * @return wrapped image generation model
     */
    ImageGenerationModel wrapImageGenerationMiddleware(
        List<ImageGenerationMiddleware> middleware);
}
