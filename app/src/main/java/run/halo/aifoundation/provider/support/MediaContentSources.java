package run.halo.aifoundation.provider.support;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.media.DataContent;

/** Wire representations for provider APIs that accept either media URLs or caller-owned data. */
public final class MediaContentSources {

    private static final UriReferencePolicy HTTP_REFERENCES =
        UriReferencePolicy.allowing("http://", "https://");
    private static final UriReferencePolicy DATA_REFERENCES =
        UriReferencePolicy.allowing("data:");

    private MediaContentSources() {
    }

    public static String urlOrDataUrl(DataContent content, String label) {
        validate(content, label);
        if (content.isUrl()) {
            return content.getUrl();
        }
        return "data:" + content.getMediaType() + ";base64," + content.getData();
    }

    public static String urlOrBase64(DataContent content, String label) {
        validate(content, label);
        if (content.isUrl()) {
            return content.getUrl();
        }
        return content.getData();
    }

    public static String mimeType(Media media) {
        if (media.getMimeType() == null) {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        }
        return media.getMimeType().toString().toLowerCase(Locale.ROOT);
    }

    public static Optional<String> urlReference(Media media) {
        var data = media.getData();
        if (data instanceof URI uri) {
            return Optional.of(uri.toString());
        }
        if (data instanceof String value && !value.isBlank()) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    public static String urlOrDataUrl(Media media, String label) {
        var reference = urlReference(media);
        if (reference.isPresent()) {
            return reference.get();
        }
        return "data:" + mimeType(media) + ";base64," + rawBase64(media, label);
    }

    public static String rawBase64(Media media, String label) {
        var data = media.getData();
        if (data instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (!(data instanceof Resource resource)) {
            throw new IllegalArgumentException(label + " must contain binary data");
        }
        try {
            return Base64.getEncoder().encodeToString(resource.getContentAsByteArray());
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Failed to read " + label, error);
        }
    }

    public static Map<String, Object> urlOrBase64Source(Media media, String label) {
        var reference = urlReference(media);
        if (reference.isEmpty()) {
            return base64Source(media, rawBase64(media, label));
        }
        var value = reference.get();
        if (HTTP_REFERENCES.allows(value)) {
            return Map.of("type", "url", "url", value);
        }
        return base64Source(media, dataUrlBase64(value, label));
    }

    private static Map<String, Object> base64Source(Media media, String data) {
        return Map.of("type", "base64", "media_type", mimeType(media), "data", data);
    }

    private static String dataUrlBase64(String value, String label) {
        var marker = ";base64,";
        if (!DATA_REFERENCES.allows(value)) {
            throw new IllegalArgumentException(
                label + " must be binary data, a data URL, or an HTTP(S) URL");
        }
        var markerIndex = value.indexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalArgumentException(label + " data URL must contain base64 data");
        }
        return value.substring(markerIndex + marker.length());
    }

    private static void validate(DataContent content, String label) {
        if (content == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        if (content.isUrl()) {
            return;
        }
        if (content.isData()) {
            return;
        }
        throw new IllegalArgumentException(label + " must contain a URL or base64 data");
    }
}
