package run.halo.aifoundation.image;

import java.beans.Transient;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.options.ProviderOptions;

/**
 * Provider-neutral request for image generation.
 *
 * <p>The runtime derives the generation mode from the media fields: prompt without input images is
 * text-to-image, input images request image-to-image or editing, and a mask requests masked
 * editing. The resolved model must support the required capability before the provider is invoked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateImageRequest {

    /**
     * Text prompt for text-to-image or image edit requests.
     *
     * <p>Most providers require a prompt even when input images are present.
     */
    private String prompt;

    /**
     * Optional input images for image-to-image or edit requests.
     *
     * <p>Each entry is validated as {@link DataContent}. URL images require native URL support by
     * the selected model/provider.
     */
    private List<DataContent> images;

    /**
     * Optional mask image for providers that support masked editing.
     */
    private DataContent mask;

    /**
     * Number of images requested.
     *
     * <p>When this exceeds the model's {@code imageGeneration.maxImagesPerCall}, the runtime may
     * split the request into several provider calls and aggregate the result.
     */
    private Integer n;

    /**
     * Provider-neutral size string such as {@code 1024x1024} when supported.
     *
     * <p>Unsupported optional controls may be ignored with an {@link ImageGenerationWarning} when
     * the core generation semantics can still succeed.
     */
    private String size;

    /**
     * Provider-neutral aspect ratio such as {@code 16:9} when supported.
     *
     * <p>Some providers accept size and aspect ratio as mutually exclusive controls. Adapter
     * behavior is reported through warnings when a field cannot be applied.
     */
    private String aspectRatio;

    /**
     * Optional deterministic seed.
     *
     * <p>Providers that do not support seed control may return a warning instead of failing the
     * whole request.
     */
    private Integer seed;

    /**
     * Preferred provider response representation.
     *
     * <p>This is a preference, not a guarantee. Check each returned
     * {@link run.halo.aifoundation.media.GeneratedFile} for URL or base64 content.
     */
    private ImageResponseFormat responseFormat;

    /**
     * Provider-specific options grouped by provider namespace.
     *
     * <p>Use this for advanced provider controls that are intentionally not part of the neutral
     * SDK surface.
     */
    private Map<String, Map<String, Object>> providerOptions;

    /**
     * Request-scoped HTTP headers sent to providers when supported.
     */
    private Map<String, String> headers;

    /**
     * Maximum retry attempts for retryable provider calls.
     *
     * <p>{@code null} means the runtime default applies.
     */
    private Integer maxRetries;

    /**
     * Maximum number of split image requests allowed to run concurrently.
     *
     * <p>This only matters when {@link #n} is larger than a model's per-call image limit.
     */
    private Integer maxParallelCalls;

    /**
     * Caller metadata exposed to lifecycle callbacks. This data is not sent to providers.
     */
    private Map<String, Object> metadata;

    /**
     * Caller context exposed to lifecycle callbacks. This data is not sent to providers.
     */
    private Map<String, Object> context;

    /**
     * Request-scoped cancellation signal.
     */
    private transient CancellationToken cancellationToken;

    /**
     * Request-scoped timeout controls.
     */
    private transient GenerationTimeouts timeouts;

    /**
     * Returns the request-scoped cancellation signal.
     *
     * @return cancellation token, or {@code null} when no request-specific token was supplied
     */
    @Transient
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    /**
     * Returns request-scoped timeout controls.
     *
     * @return timeout settings, or {@code null} when runtime defaults should apply
     */
    @Transient
    public GenerationTimeouts getTimeouts() {
        return timeouts;
    }

    /**
     * Builder extensions for request-specific provider options.
     */
    public static class GenerateImageRequestBuilder {
        /**
         * Sets a provider-neutral or provider-specific size string.
         *
         * <p>Use this overload when the provider expects a label that is not naturally expressed
         * as width and height. For common pixel dimensions, prefer {@link #size(int)} or
         * {@link #size(int, int)}.
         *
         * @param size size string such as {@code 1024x1024}
         * @return this builder
         */
        public GenerateImageRequestBuilder size(String size) {
            this.size = size;
            return this;
        }

        /**
         * Sets a square image size.
         *
         * <p>This is a convenience for providers that use the common {@code WIDTHxHEIGHT} size
         * string format. For example, {@code size(1024)} stores {@code 1024x1024}. Callers can
         * still use the generated {@code size(String)} builder method for provider-specific size
         * labels.
         *
         * @param edge square edge length in pixels
         * @return this builder
         * @throws IllegalArgumentException when {@code edge} is not positive
         */
        public GenerateImageRequestBuilder size(int edge) {
            requirePositiveDimension(edge, "edge");
            this.size = edge + "x" + edge;
            return this;
        }

        /**
         * Sets an explicit image size from width and height.
         *
         * <p>This is a convenience for providers that use the common {@code WIDTHxHEIGHT} size
         * string format. For example, {@code size(1024, 768)} stores {@code 1024x768}. Callers can
         * still use the generated {@code size(String)} builder method for provider-specific size
         * labels.
         *
         * @param width width in pixels
         * @param height height in pixels
         * @return this builder
         * @throws IllegalArgumentException when either dimension is not positive
         */
        public GenerateImageRequestBuilder size(int width, int height) {
            requirePositiveDimension(width, "width");
            requirePositiveDimension(height, "height");
            this.size = width + "x" + height;
            return this;
        }

        /**
         * Sets provider-specific options grouped by provider namespace.
         *
         * @param providerOptions namespace to option map
         * @return this builder
         */
        public GenerateImageRequestBuilder providerOptions(
            Map<String, Map<String, Object>> providerOptions) {
            this.providerOptions = providerOptions;
            return this;
        }

        /**
         * Sets provider-specific options using typed namespace helpers.
         *
         * @param options provider option namespaces
         * @return this builder
         */
        public GenerateImageRequestBuilder providerOptions(
            ProviderOptions.NamespaceOptions... options) {
            this.providerOptions = ProviderOptions.of(options);
            return this;
        }
    }

    private static void requirePositiveDimension(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
