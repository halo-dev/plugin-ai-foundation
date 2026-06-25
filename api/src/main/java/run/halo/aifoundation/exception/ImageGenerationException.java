package run.halo.aifoundation.exception;

/**
 * Raised when an image generation provider response cannot be treated as a successful result.
 *
 * <p>This exception is used for failed image-generation semantics after a provider call, for
 * example when the provider returned no usable image. Capability and media validation failures use
 * more specific exception types before the provider is invoked.
 */
public class ImageGenerationException extends AiFoundationException {

    /**
     * Halo model resource name involved in the failed generation.
     */
    private final String modelName;

    /**
     * Halo provider resource name involved in the failed generation.
     */
    private final String providerName;

    /**
     * Provider type identifier involved in the failed generation.
     */
    private final String providerType;

    /**
     * Creates an image generation exception with safe model and provider identifiers.
     *
     * @param message log-safe error message
     * @param modelName Halo model resource name
     * @param providerName Halo provider resource name
     * @param providerType provider type identifier
     */
    public ImageGenerationException(String message, String modelName, String providerName,
        String providerType) {
        super(message);
        this.modelName = modelName;
        this.providerName = providerName;
        this.providerType = providerType;
    }

    /**
     * Returns the Halo model resource name involved in the failed generation.
     *
     * @return model resource name, or {@code null} when unavailable
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Returns the Halo provider resource name involved in the failed generation.
     *
     * @return provider resource name, or {@code null} when unavailable
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Returns the provider type identifier involved in the failed generation.
     *
     * @return provider type, or {@code null} when unavailable
     */
    public String getProviderType() {
        return providerType;
    }
}
