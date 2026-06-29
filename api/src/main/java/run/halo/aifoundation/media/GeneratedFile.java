package run.halo.aifoundation.media;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral generated file returned by a model invocation.
 *
 * <p>Providers may return either a URL or base64 content. AI Foundation exposes the provider value
 * as-is and does not download generated URLs. Consumer plugins decide whether to persist,
 * proxy, transform, or discard the returned file.
 */
@Data
@Builder(buildMethodName = "uncheckedBuild")
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedFile {

    /**
     * Optional provider or Halo-side file identifier.
     */
    private String id;

    /**
     * Provider-returned file URL. The runtime does not download it into {@link #base64}.
     */
    private String url;

    /**
     * Base64 encoded file content returned by the provider.
     */
    private String base64;

    /**
     * Media type such as {@code image/png}, when available.
     */
    private String mediaType;

    /**
     * Optional filename returned by the provider or chosen by the caller.
     */
    private String filename;

    /**
     * Optional display title for UI message file parts or caller UIs.
     */
    private String title;

    /**
     * Sanitized provider or caller metadata associated with this file.
     */
    private Map<String, Object> metadata;

    /**
     * Creates a URL-backed generated file.
     *
     * @param url provider-returned file URL
     * @param mediaType media type such as {@code image/png}
     * @return URL-backed generated file
     */
    public static GeneratedFile url(String url, String mediaType) {
        return url(url, mediaType, null);
    }

    /**
     * Creates a URL-backed generated file with a filename.
     *
     * @param url provider-returned file URL
     * @param mediaType media type such as {@code image/png}
     * @param filename optional filename
     * @return URL-backed generated file
     */
    public static GeneratedFile url(String url, String mediaType, String filename) {
        return GeneratedFile.builder()
            .url(url)
            .mediaType(mediaType)
            .filename(filename)
            .build();
    }

    /**
     * Creates a base64-backed generated file.
     *
     * @param base64 base64 encoded file content
     * @param mediaType media type such as {@code image/png}
     * @return base64-backed generated file
     */
    public static GeneratedFile base64(String base64, String mediaType) {
        return base64(base64, mediaType, null);
    }

    /**
     * Creates a base64-backed generated file with a filename.
     *
     * @param base64 base64 encoded file content
     * @param mediaType media type such as {@code image/png}
     * @param filename optional filename
     * @return base64-backed generated file
     */
    public static GeneratedFile base64(String base64, String mediaType, String filename) {
        return GeneratedFile.builder()
            .base64(base64)
            .mediaType(mediaType)
            .filename(filename)
            .build();
    }

    /**
     * Returns whether this file is represented by a URL.
     *
     * @return {@code true} when {@link #url} is set
     */
    public boolean isUrl() {
        return hasText(url);
    }

    /**
     * Returns whether this file is represented by base64 content.
     *
     * @return {@code true} when {@link #base64} is set
     */
    public boolean isBase64() {
        return hasText(base64);
    }

    private GeneratedFile validate() {
        if (hasText(url) == hasText(base64)) {
            throw new IllegalArgumentException(
                "generated file must set exactly one of url or base64");
        }
        return this;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Validating builder for {@link GeneratedFile}.
     */
    public static class GeneratedFileBuilder {
        /**
         * Builds and validates a generated file value.
         *
         * @return validated generated file
         * @throws IllegalArgumentException when neither or both of {@link #url} and {@link #base64}
         *                                  are set
         */
        public GeneratedFile build() {
            return uncheckedBuild().validate();
        }
    }
}
