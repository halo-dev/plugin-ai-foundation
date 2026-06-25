package run.halo.aifoundation.image.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.exception.ImageGenerationException;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.image.ImageGenerationRequests;
import run.halo.aifoundation.image.ImageGenerationResults;

/**
 * Helpers for composing image generation middleware.
 */
public final class ImageGenerationMiddlewares {

    private ImageGenerationMiddlewares() {
    }

    /**
     * Wraps a model with model-level middleware.
     *
     * <p>Middleware is applied in list order. Request-level middleware attached to
     * {@link GenerateImageRequest} runs inside model-level middleware and also preserves list order.
     *
     * @param model base model
     * @param middleware model-level middleware
     * @return wrapped image generation model
     */
    public static ImageGenerationModel wrap(ImageGenerationModel model,
        List<ImageGenerationMiddleware> middleware) {
        Objects.requireNonNull(model, "model must not be null");
        var chain = List.copyOf(Objects.requireNonNull(middleware, "middleware must not be null"));
        if (chain.isEmpty()) {
            return model;
        }
        if (model instanceof ImageGenerationMiddlewareAware aware) {
            return aware.wrapImageGenerationMiddleware(chain);
        }
        return new WrappedImageGenerationModel(model, chain);
    }

    /**
     * Wraps a model with model-level middleware.
     *
     * @param model base model
     * @param middleware model-level middleware
     * @return wrapped image generation model
     */
    public static ImageGenerationModel wrap(ImageGenerationModel model,
        ImageGenerationMiddleware... middleware) {
        return wrap(model, List.of(middleware));
    }

    /**
     * Applies request-level middleware attached to a generation request.
     *
     * @param model terminal model
     * @param request request that may contain request-level middleware
     * @return image generation result
     */
    public static Mono<GenerateImageResult> applyRequestMiddleware(ImageGenerationModel model,
        GenerateImageRequest request) {
        return generate(model, request, requestMiddleware(request));
    }

    /**
     * Creates middleware that fills omitted request settings from a defaults request.
     *
     * <p>The helper does not set prompt, input images, mask, cancellation, timeout, or middleware.
     *
     * @param defaults default request settings
     * @return default-setting middleware
     */
    public static ImageGenerationMiddleware defaultSettings(GenerateImageRequest defaults) {
        Objects.requireNonNull(defaults, "defaults must not be null");
        return new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageRequest> transformRequest(ImageGenerationContext context) {
                return Mono.fromSupplier(() -> mergeDefaults(context.request(), defaults));
            }
        };
    }

    /**
     * Creates middleware from a synchronous request mapper.
     *
     * @param mapper request mapper
     * @return request mapping middleware
     */
    public static ImageGenerationMiddleware mapRequest(
        Function<GenerateImageRequest, GenerateImageRequest> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageRequest> transformRequest(ImageGenerationContext context) {
                return Mono.fromSupplier(() -> Objects.requireNonNull(
                    mapper.apply(context.request()), "mapped request must not be null"));
            }
        };
    }

    /**
     * Creates middleware from a synchronous result mapper.
     *
     * @param mapper result mapper
     * @return result mapping middleware
     */
    public static ImageGenerationMiddleware mapResult(
        Function<GenerateImageResult, GenerateImageResult> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
                GenerateImageNext next) {
                return next.generate(context.request())
                    .map(result -> Objects.requireNonNull(mapper.apply(result),
                        "mapped result must not be null"));
            }
        };
    }

    private static Mono<GenerateImageResult> generate(ImageGenerationModel model,
        GenerateImageRequest request, List<ImageGenerationMiddleware> middleware) {
        Objects.requireNonNull(model, "model must not be null");
        var effectiveRequest = request != null ? request : GenerateImageRequest.builder().build();
        return generateAt(model, effectiveRequest, middleware, 0)
            .switchIfEmpty(Mono.error(() -> new ImageGenerationException(
                "Image generation returned no images", modelName(model), providerName(model),
                providerType(model))))
            .map(result -> ImageGenerationResults.requireImages(result, modelName(model),
                providerName(model), providerType(model)));
    }

    private static Mono<GenerateImageResult> generateAt(ImageGenerationModel model,
        GenerateImageRequest request, List<ImageGenerationMiddleware> middleware, int index) {
        return Mono.defer(() -> {
            ImageGenerationRequests.requireValidShape(request);
            if (index >= middleware.size()) {
                return model.generateImage(ImageGenerationRequests.withoutMiddleware(request));
            }
            var current = middleware.get(index);
            return current.wrapGenerate(new ImageGenerationContext(model, request),
                nextRequest -> generateAt(model, nextRequest, middleware, index + 1));
        });
    }

    private static List<ImageGenerationMiddleware> requestMiddleware(GenerateImageRequest request) {
        if (request == null || request.getMiddleware() == null || request.getMiddleware().isEmpty()) {
            return List.of();
        }
        return List.copyOf(request.getMiddleware());
    }

    private static GenerateImageRequest mergeDefaults(GenerateImageRequest request,
        GenerateImageRequest defaults) {
        return ImageGenerationRequests.builderFrom(request)
            .n(request.getN() != null ? request.getN() : defaults.getN())
            .size(request.getSize() != null ? request.getSize() : defaults.getSize())
            .aspectRatio(request.getAspectRatio() != null
                ? request.getAspectRatio()
                : defaults.getAspectRatio())
            .seed(request.getSeed() != null ? request.getSeed() : defaults.getSeed())
            .responseFormat(request.getResponseFormat() != null
                ? request.getResponseFormat()
                : defaults.getResponseFormat())
            .providerOptions(request.getProviderOptions() != null
                ? request.getProviderOptions()
                : defaults.getProviderOptions())
            .headers(request.getHeaders() != null ? request.getHeaders() : defaults.getHeaders())
            .maxRetries(request.getMaxRetries() != null
                ? request.getMaxRetries()
                : defaults.getMaxRetries())
            .maxParallelCalls(request.getMaxParallelCalls() != null
                ? request.getMaxParallelCalls()
                : defaults.getMaxParallelCalls())
            .metadata(request.getMetadata() != null ? request.getMetadata() : defaults.getMetadata())
            .context(request.getContext() != null ? request.getContext() : defaults.getContext())
            .build();
    }

    private static String modelName(ImageGenerationModel model) {
        return model.modelInfo() != null ? model.modelInfo().getName() : "unknown";
    }

    private static String providerName(ImageGenerationModel model) {
        return model.providerInfo() != null ? model.providerInfo().getName() : "unknown";
    }

    private static String providerType(ImageGenerationModel model) {
        return model.providerInfo() != null ? model.providerInfo().getProviderType() : null;
    }

    private static final class WrappedImageGenerationModel implements ImageGenerationModel {
        private final ImageGenerationModel delegate;
        private final List<ImageGenerationMiddleware> middleware;

        private WrappedImageGenerationModel(ImageGenerationModel delegate,
            List<ImageGenerationMiddleware> middleware) {
            this.delegate = delegate;
            this.middleware = middleware;
        }

        @Override
        public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
            var chain = new ArrayList<>(middleware);
            chain.addAll(requestMiddleware(request));
            return generate(delegate, request, chain);
        }

        @Override
        public run.halo.aifoundation.capability.ModelCapabilities capabilities() {
            return delegate.capabilities();
        }

        @Override
        public run.halo.aifoundation.model.ModelInfo modelInfo() {
            return delegate.modelInfo();
        }

        @Override
        public run.halo.aifoundation.model.ProviderInfo providerInfo() {
            return delegate.providerInfo();
        }
    }
}
