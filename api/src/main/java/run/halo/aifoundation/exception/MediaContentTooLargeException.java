package run.halo.aifoundation.exception;

/**
 * Raised when media content exceeds framework resource limits.
 *
 * <p>This is a technical resource guard, not a business upload policy. Consumer plugins remain
 * responsible for deciding which files users may choose for their own workflows.
 */
public class MediaContentTooLargeException extends AiFoundationException {

    /**
     * Configured maximum byte count for the failing limit.
     */
    private final long maxBytes;

    /**
     * Actual byte count observed for the failing limit.
     */
    private final long actualBytes;

    /**
     * Limit scope, for example a single media part or total request media.
     */
    private final String scope;

    /**
     * Media type associated with the oversized content, when available.
     */
    private final String mediaType;

    /**
     * Filename associated with the oversized content, when available.
     */
    private final String filename;

    /**
     * Zero-based message index for language-message validation failures, when available.
     */
    private final Integer messageIndex;

    /**
     * Zero-based part index for language-message validation failures, when available.
     */
    private final Integer partIndex;

    /**
     * Creates an oversized-media exception for a limit scope.
     *
     * @param scope limit scope
     * @param maxBytes configured maximum bytes
     * @param actualBytes actual observed bytes
     */
    public MediaContentTooLargeException(String scope, long maxBytes, long actualBytes) {
        this(scope, maxBytes, actualBytes, null, null, null, null);
    }

    /**
     * Creates an oversized-media exception with safe diagnostic fields.
     *
     * @param scope limit scope
     * @param maxBytes configured maximum bytes
     * @param actualBytes actual observed bytes
     * @param mediaType media type, when available
     * @param filename filename, when available
     * @param messageIndex zero-based message index, when available
     * @param partIndex zero-based part index, when available
     */
    public MediaContentTooLargeException(String scope, long maxBytes, long actualBytes,
        String mediaType, String filename, Integer messageIndex, Integer partIndex) {
        super("Media content exceeds " + scope + " limit: maxBytes=" + maxBytes
            + ", actualBytes=" + actualBytes);
        this.scope = scope;
        this.maxBytes = maxBytes;
        this.actualBytes = actualBytes;
        this.mediaType = mediaType;
        this.filename = filename;
        this.messageIndex = messageIndex;
        this.partIndex = partIndex;
    }

    /**
     * Returns the configured maximum byte count.
     *
     * @return configured maximum bytes
     */
    public long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Returns the actual observed byte count.
     *
     * @return actual observed bytes
     */
    public long getActualBytes() {
        return actualBytes;
    }

    /**
     * Returns the limit scope.
     *
     * @return limit scope
     */
    public String getScope() {
        return scope;
    }

    /**
     * Returns the media type associated with the oversized content.
     *
     * @return media type, or {@code null} when unavailable
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Returns the filename associated with the oversized content.
     *
     * @return filename, or {@code null} when unavailable
     */
    public String getFilename() {
        return filename;
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
