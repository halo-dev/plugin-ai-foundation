package run.halo.aifoundation.exception;

/**
 * Raised when caller-provided media content has an invalid provider-neutral structure.
 *
 * <p>This exception is raised before provider invocation. Callers can use the optional
 * message/part indexes to highlight the failing chat message part, and the media fields to show a
 * recoverable validation prompt without exposing media data.
 */
public class InvalidMediaContentException extends AiFoundationException {

    /**
     * Media type associated with the invalid content, when available.
     */
    private final String mediaType;

    /**
     * Filename associated with the invalid content, when available.
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
     * Creates an invalid media exception with only a message.
     *
     * @param message log-safe error message
     */
    public InvalidMediaContentException(String message) {
        this(message, null, null, null, null);
    }

    /**
     * Creates an invalid media exception with safe diagnostic fields.
     *
     * @param message log-safe error message
     * @param mediaType media type, when available
     * @param filename filename, when available
     * @param messageIndex zero-based message index, when available
     * @param partIndex zero-based part index, when available
     */
    public InvalidMediaContentException(String message, String mediaType, String filename,
        Integer messageIndex, Integer partIndex) {
        super(message);
        this.mediaType = mediaType;
        this.filename = filename;
        this.messageIndex = messageIndex;
        this.partIndex = partIndex;
    }

    /**
     * Creates an invalid media exception with safe diagnostic fields and a cause.
     *
     * @param message log-safe error message
     * @param mediaType media type, when available
     * @param filename filename, when available
     * @param messageIndex zero-based message index, when available
     * @param partIndex zero-based part index, when available
     * @param cause underlying validation cause
     */
    public InvalidMediaContentException(String message, String mediaType, String filename,
        Integer messageIndex, Integer partIndex, Throwable cause) {
        super(message, cause);
        this.mediaType = mediaType;
        this.filename = filename;
        this.messageIndex = messageIndex;
        this.partIndex = partIndex;
    }

    /**
     * Returns the media type associated with the invalid content.
     *
     * @return media type, or {@code null} when unavailable
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Returns the filename associated with the invalid content.
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
