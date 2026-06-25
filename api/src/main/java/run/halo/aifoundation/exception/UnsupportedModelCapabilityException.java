package run.halo.aifoundation.exception;

/**
 * Raised when a resolved model does not satisfy a required semantic capability.
 *
 * <p>This exception is raised before provider invocation for capability failures such as image
 * input on a text-only model, URL media on a data-only model, or image generation modes the model
 * does not support. Callers should use the typed fields instead of parsing the message text.
 */
public class UnsupportedModelCapabilityException extends AiFoundationException {

    /**
     * Halo model resource name that failed the capability check.
     */
    private final String modelName;

    /**
     * Halo provider resource name for the model, when available.
     */
    private final String providerName;

    /**
     * Provider type identifier for the model, when available.
     */
    private final String providerType;

    /**
     * Stable capability path, for example {@code language.imageInput}.
     */
    private final String capabilityPath;

    /**
     * Required value or condition.
     */
    private final Object expectedValue;

    /**
     * Actual value from the model capability snapshot, when available.
     */
    private final Object actualValue;

    /**
     * Zero-based message index for language-message validation failures, when available.
     */
    private final Integer messageIndex;

    /**
     * Zero-based part index for language-message validation failures, when available.
     */
    private final Integer partIndex;

    /**
     * Creates an unsupported-capability exception without message-part coordinates.
     *
     * @param modelName Halo model resource name
     * @param providerName Halo provider resource name, when available
     * @param providerType provider type identifier, when available
     * @param capabilityPath stable capability path
     * @param expectedValue required value or condition
     * @param actualValue actual value, when available
     */
    public UnsupportedModelCapabilityException(String modelName, String providerName,
        String providerType, String capabilityPath, Object expectedValue, Object actualValue) {
        this(modelName, providerName, providerType, capabilityPath, expectedValue, actualValue,
            null, null);
    }

    /**
     * Creates an unsupported-capability exception with optional message-part coordinates.
     *
     * @param modelName Halo model resource name
     * @param providerName Halo provider resource name, when available
     * @param providerType provider type identifier, when available
     * @param capabilityPath stable capability path
     * @param expectedValue required value or condition
     * @param actualValue actual value, when available
     * @param messageIndex zero-based message index, when available
     * @param partIndex zero-based part index, when available
     */
    public UnsupportedModelCapabilityException(String modelName, String providerName,
        String providerType, String capabilityPath, Object expectedValue, Object actualValue,
        Integer messageIndex, Integer partIndex) {
        super("AI model '" + modelName + "' does not support required capability '"
            + capabilityPath + "'");
        this.modelName = modelName;
        this.providerName = providerName;
        this.providerType = providerType;
        this.capabilityPath = capabilityPath;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.messageIndex = messageIndex;
        this.partIndex = partIndex;
    }

    /**
     * Returns the Halo model resource name that failed the capability check.
     *
     * @return model resource name
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Returns the Halo provider resource name for the model.
     *
     * @return provider resource name, or {@code null} when unavailable
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Returns the provider type identifier for the model.
     *
     * @return provider type, or {@code null} when unavailable
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * Returns the stable capability path that failed.
     *
     * @return capability path
     */
    public String getCapabilityPath() {
        return capabilityPath;
    }

    /**
     * Returns the required value or condition.
     *
     * @return expected value
     */
    public Object getExpectedValue() {
        return expectedValue;
    }

    /**
     * Returns the actual value from the model capability snapshot.
     *
     * @return actual value, or {@code null} when unavailable
     */
    public Object getActualValue() {
        return actualValue;
    }

    /**
     * Returns the zero-based message index for language-message validation failures.
     *
     * @return message index, or {@code null} when unavailable
     */
    public Integer getMessageIndex() {
        return messageIndex;
    }

    /**
     * Returns the zero-based part index for language-message validation failures.
     *
     * @return part index, or {@code null} when unavailable
     */
    public Integer getPartIndex() {
        return partIndex;
    }
}
