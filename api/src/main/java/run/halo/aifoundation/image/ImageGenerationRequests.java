package run.halo.aifoundation.image;

import java.util.List;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.image.middleware.ImageGenerationMiddleware;

/**
 * Utility methods for composing and validating image generation requests.
 */
public final class ImageGenerationRequests {

    private ImageGenerationRequests() {
    }

    /**
     * Creates a builder initialized from an existing request.
     *
     * <p>Request-scoped middleware, cancellation, and timeout controls are preserved.
     *
     * @param request source request
     * @return initialized request builder
     */
    public static GenerateImageRequest.GenerateImageRequestBuilder builderFrom(
        GenerateImageRequest request) {
        requireNonNullRequest(request);
        return GenerateImageRequest.builder()
            .prompt(request.getPrompt())
            .images(request.getImages())
            .mask(request.getMask())
            .n(request.getN())
            .size(request.getSize())
            .aspectRatio(request.getAspectRatio())
            .seed(request.getSeed())
            .responseFormat(request.getResponseFormat())
            .providerOptions(request.getProviderOptions())
            .headers(request.getHeaders())
            .maxRetries(request.getMaxRetries())
            .maxParallelCalls(request.getMaxParallelCalls())
            .metadata(request.getMetadata())
            .context(request.getContext())
            .cancellationToken(request.getCancellationToken())
            .timeouts(request.getTimeouts())
            .middleware(request.getMiddleware());
    }

    /**
     * Creates a request copy without request-scoped middleware.
     *
     * <p>Middleware runtimes use this before invoking the terminal model so the same request-level
     * middleware is not applied recursively.
     *
     * @param request source request
     * @return copied request without middleware
     */
    public static GenerateImageRequest withoutMiddleware(GenerateImageRequest request) {
        return builderFrom(request)
            .middleware((List<ImageGenerationMiddleware>) null)
            .build();
    }

    /**
     * Validates the public request shape that must hold even when middleware short-circuits before a
     * provider call.
     *
     * <p>This validation intentionally excludes provider capability checks and provider resource
     * policy checks. Those remain provider-execution concerns.
     *
     * @param request image generation request
     * @return the same request when valid
     * @throws IllegalArgumentException when required scalar request fields are invalid
     * @throws InvalidMediaContentException when image or mask entries are structurally invalid
     */
    public static GenerateImageRequest requireValidShape(GenerateImageRequest request) {
        requireNonNullRequest(request);
        if (!hasText(request.getPrompt())) {
            throw new IllegalArgumentException("Image generation prompt is required");
        }
        if (request.getN() != null && request.getN() <= 0) {
            throw new IllegalArgumentException("Image generation n must be positive");
        }
        if (request.getMaxRetries() != null && request.getMaxRetries() < 0) {
            throw new IllegalArgumentException("Image generation maxRetries must not be negative");
        }
        if (request.getMaxParallelCalls() != null && request.getMaxParallelCalls() <= 0) {
            throw new IllegalArgumentException("Image generation maxParallelCalls must be positive");
        }
        if (request.getMask() != null && !hasInputImages(request)) {
            throw new IllegalArgumentException(
                "Image generation mask requires at least one input image");
        }
        validateMediaEntries(request);
        return request;
    }

    private static void requireNonNullRequest(GenerateImageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Generate image request is required");
        }
    }

    private static void validateMediaEntries(GenerateImageRequest request) {
        if (request.getImages() != null) {
            for (var image : request.getImages()) {
                validateImageMedia(image, "image");
            }
        }
        if (request.getMask() == null) {
            return;
        }
        validateImageMedia(request.getMask(), "mask");
    }

    private static void validateImageMedia(run.halo.aifoundation.media.DataContent media,
        String name) {
        if (media == null) {
            throw new InvalidMediaContentException(name + " media content must not be null");
        }
        if (media.isUrl() == media.isData()) {
            throw new InvalidMediaContentException(
                name + " media content must set exactly one of url or data",
                media.getMediaType(), media.getFilename(), null, null);
        }
        if (!hasText(media.getMediaType())) {
            throw new InvalidMediaContentException(
                name + " mediaType is required for image generation media",
                media.getMediaType(), media.getFilename(), null, null);
        }
        if (!isImageMediaType(media.getMediaType())) {
            throw new InvalidMediaContentException(
                name + " mediaType must match image/*",
                media.getMediaType(), media.getFilename(), null, null);
        }
    }

    private static boolean hasInputImages(GenerateImageRequest request) {
        return request.getImages() != null && !request.getImages().isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isImageMediaType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase().startsWith("image/");
    }
}
