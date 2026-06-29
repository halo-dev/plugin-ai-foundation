package run.halo.aifoundation.image.middleware;

import java.util.Objects;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.model.ModelInfo;
import run.halo.aifoundation.model.ProviderInfo;

/**
 * Context passed to image generation middleware.
 *
 * @param model model being wrapped
 * @param request current image generation request
 * @param capabilities read-only capability snapshot for the resolved model
 * @param modelInfo stable model identity, or {@code null} when the model is not managed by AI
 *                  Foundation
 * @param providerInfo stable provider identity, or {@code null} when the model is not managed by AI
 *                     Foundation
 */
public record ImageGenerationContext(ImageGenerationModel model,
                                     GenerateImageRequest request,
                                     ModelCapabilities capabilities,
                                     ModelInfo modelInfo,
                                     ProviderInfo providerInfo) {

    /**
     * Creates a context and fills missing capability information with an empty snapshot.
     */
    public ImageGenerationContext {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(request, "request must not be null");
        capabilities = capabilities != null ? capabilities : ModelCapabilities.empty();
    }

    /**
     * Creates a context from a model and request.
     *
     * @param model model being wrapped
     * @param request current request
     */
    public ImageGenerationContext(ImageGenerationModel model, GenerateImageRequest request) {
        this(model, request, model != null ? model.capabilities() : ModelCapabilities.empty(),
            model != null ? model.modelInfo() : null,
            model != null ? model.providerInfo() : null);
    }
}
