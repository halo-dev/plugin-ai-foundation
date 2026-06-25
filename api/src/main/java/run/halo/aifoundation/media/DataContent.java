package run.halo.aifoundation.media;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.exception.InvalidMediaContentException;

/**
 * Provider-neutral caller-provided media content.
 *
 * <p>A value is either URL-backed or base64-data-backed. URL values are never downloaded by the
 * SDK and regular URLs never infer {@link #mediaType} from a file extension.
 */
@Data
@Builder(buildMethodName = "uncheckedBuild")
@NoArgsConstructor
@AllArgsConstructor
public class DataContent {

    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64";

    /**
     * Provider-native media URL, when the target model supports URL input.
     *
     * <p>The runtime validates URL structure and model capability, but does not download the
     * remote resource.
     */
    private String url;

    /**
     * Base64 encoded media data without a data URL prefix.
     */
    private String data;

    /**
     * Media type such as {@code image/png}. Required for data-backed content.
     */
    private String mediaType;

    /**
     * Optional caller-supplied filename for diagnostics and provider adapters that support it.
     */
    private String filename;

    /**
     * Creates URL-backed media content without a media type.
     *
     * <p>Regular URLs do not infer a media type from their path or extension. Some model
     * capabilities or providers may still require callers to provide one through
     * {@link #url(String, String)}.
     *
     * @param url provider-native media URL
     * @return URL-backed media content
     */
    public static DataContent url(String url) {
        return url(url, null, null);
    }

    /**
     * Creates URL-backed media content with an explicit media type.
     *
     * @param url provider-native media URL
     * @param mediaType media type such as {@code image/png}; may be {@code null} when the target
     *                  model does not require it
     * @return URL-backed media content
     */
    public static DataContent url(String url, String mediaType) {
        return url(url, mediaType, null);
    }

    /**
     * Creates URL-backed media content with optional diagnostics metadata.
     *
     * @param url provider-native media URL
     * @param mediaType media type such as {@code image/png}; may be {@code null} when the target
     *                  model does not require it
     * @param filename optional filename for diagnostics and provider adapters
     * @return URL-backed media content
     */
    public static DataContent url(String url, String mediaType, String filename) {
        return DataContent.builder()
            .url(url)
            .mediaType(mediaType)
            .filename(filename)
            .build();
    }

    /**
     * Creates data-backed media content from a base64 string.
     *
     * @param base64Data base64 encoded media bytes without a data URL prefix
     * @param mediaType required media type such as {@code image/png}
     * @return data-backed media content
     * @throws InvalidMediaContentException when the data is blank, not valid base64, or the media
     *                                      type is missing
     */
    public static DataContent data(String base64Data, String mediaType) {
        return data(base64Data, mediaType, null);
    }

    /**
     * Creates data-backed media content from a base64 string with optional diagnostics metadata.
     *
     * @param base64Data base64 encoded media bytes without a data URL prefix
     * @param mediaType required media type such as {@code image/png}
     * @param filename optional filename for diagnostics and provider adapters
     * @return data-backed media content
     * @throws InvalidMediaContentException when the data is blank, not valid base64, or the media
     *                                      type is missing
     */
    public static DataContent data(String base64Data, String mediaType, String filename) {
        validateBase64(base64Data, mediaType, filename);
        return DataContent.builder()
            .data(base64Data)
            .mediaType(mediaType)
            .filename(filename)
            .build();
    }

    /**
     * Creates data-backed media content from raw bytes.
     *
     * @param bytes raw media bytes
     * @param mediaType required media type such as {@code image/png}
     * @return data-backed media content
     * @throws InvalidMediaContentException when the bytes are empty or the media type is missing
     */
    public static DataContent data(byte[] bytes, String mediaType) {
        return data(bytes, mediaType, null);
    }

    /**
     * Creates data-backed media content from raw bytes with optional diagnostics metadata.
     *
     * @param bytes raw media bytes
     * @param mediaType required media type such as {@code image/png}
     * @param filename optional filename for diagnostics and provider adapters
     * @return data-backed media content
     * @throws InvalidMediaContentException when the bytes are empty or the media type is missing
     */
    public static DataContent data(byte[] bytes, String mediaType, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw invalid("media data must not be empty", mediaType, filename);
        }
        return data(Base64.getEncoder().encodeToString(bytes), mediaType, filename);
    }

    /**
     * Creates data-backed media content from a {@code data:<mediaType>;base64,...} URL.
     *
     * <p>The returned value stores normalized base64 data and media type separately; it does not
     * keep the full data URL as {@link #url}.
     *
     * @param dataUrl data URL using base64 encoding
     * @return normalized data-backed media content
     * @throws InvalidMediaContentException when the data URL is malformed
     */
    public static DataContent dataUrl(String dataUrl) {
        return dataUrl(dataUrl, null);
    }

    /**
     * Creates data-backed media content from a data URL with optional diagnostics metadata.
     *
     * @param dataUrl data URL using base64 encoding
     * @param filename optional filename for diagnostics and provider adapters
     * @return normalized data-backed media content
     * @throws InvalidMediaContentException when the data URL is malformed
     */
    public static DataContent dataUrl(String dataUrl, String filename) {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw invalid("data URL must not be blank", null, filename);
        }
        if (!dataUrl.regionMatches(true, 0, DATA_URL_PREFIX, 0, DATA_URL_PREFIX.length())) {
            throw invalid("data URL must start with data:", null, filename);
        }
        var commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0) {
            throw invalid("data URL must contain a comma separator", null, filename);
        }
        var metadata = dataUrl.substring(DATA_URL_PREFIX.length(), commaIndex);
        var base64Data = dataUrl.substring(commaIndex + 1);
        var markerIndex = metadata.toLowerCase(Locale.ROOT).indexOf(BASE64_MARKER);
        if (markerIndex < 0) {
            throw invalid("data URL must use base64 encoding", null, filename);
        }
        var mediaType = metadata.substring(0, markerIndex);
        if (mediaType == null || mediaType.isBlank()) {
            throw invalid("data URL media type must not be blank", null, filename);
        }
        return data(base64Data, mediaType, filename);
    }

    /**
     * Returns whether this content uses a provider-native URL source.
     *
     * @return {@code true} when {@link #url} is set
     */
    public boolean isUrl() {
        return hasText(url);
    }

    /**
     * Returns whether this content uses caller-provided data.
     *
     * @return {@code true} when {@link #data} is set
     */
    public boolean isData() {
        return hasText(data);
    }

    /**
     * Returns the source kind represented by this content.
     *
     * @return {@link InputSource#URL}, {@link InputSource#DATA}, or {@code null} when the value has
     * not been populated yet
     */
    public InputSource source() {
        if (isUrl()) {
            return InputSource.URL;
        }
        if (isData()) {
            return InputSource.DATA;
        }
        return null;
    }

    /**
     * Decodes {@link #data} when this value is data-backed.
     *
     * @return decoded bytes, or {@code null} for URL-backed content
     * @throws InvalidMediaContentException when the stored data is not valid base64
     */
    public byte[] decodedData() {
        if (!isData()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(data.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            throw invalid("media data must be valid base64", mediaType, filename, e);
        }
    }

    private DataContent validate() {
        if (hasText(url) == hasText(data)) {
            throw invalid("media content must set exactly one of url or data", mediaType, filename);
        }
        if (hasText(data)) {
            if (!hasText(mediaType)) {
                throw invalid("data-backed media content must include mediaType", mediaType,
                    filename);
            }
            validateBase64(data, mediaType, filename);
        }
        if (hasText(url) && url.strip().length() != url.length()) {
            throw invalid("media URL must not contain leading or trailing whitespace", mediaType,
                filename);
        }
        return this;
    }

    private static void validateBase64(String base64Data, String mediaType, String filename) {
        if (!hasText(base64Data)) {
            throw invalid("media data must not be blank", mediaType, filename);
        }
        try {
            Base64.getDecoder().decode(base64Data.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            throw invalid("media data must be valid base64", mediaType, filename, e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static InvalidMediaContentException invalid(String message, String mediaType,
        String filename) {
        return new InvalidMediaContentException(message, mediaType, filename, null, null);
    }

    private static InvalidMediaContentException invalid(String message, String mediaType,
        String filename, Throwable cause) {
        return new InvalidMediaContentException(message, mediaType, filename, null, null, cause);
    }

    /**
     * Validating builder for {@link DataContent}.
     */
    public static class DataContentBuilder {
        /**
         * Builds and validates a media content value.
         *
         * @return validated media content
         * @throws InvalidMediaContentException when the content is structurally invalid
         */
        public DataContent build() {
            return uncheckedBuild().validate();
        }
    }
}
