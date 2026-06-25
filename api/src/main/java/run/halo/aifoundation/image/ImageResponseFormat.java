package run.halo.aifoundation.image;

/**
 * Preferred provider response representation for generated images.
 */
public enum ImageResponseFormat {
    /**
     * Prefer provider-returned URLs.
     */
    URL,

    /**
     * Prefer base64 encoded file content.
     */
    BASE64
}
