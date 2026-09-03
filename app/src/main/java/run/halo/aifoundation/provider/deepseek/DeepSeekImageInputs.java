package run.halo.aifoundation.provider.deepseek;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.content.Media;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.provider.support.MediaContentSources;
import run.halo.aifoundation.provider.support.UriReferencePolicy;

/** DeepSeek image validation and wire representations for its language protocols. */
final class DeepSeekImageInputs {

    private static final int MAX_EXTERNAL_IMAGE_URL_LENGTH = 8192;
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final UriReferencePolicy IMAGE_REFERENCES =
        UriReferencePolicy.allowing("http://", "https://", "data:");
    private static final UriReferencePolicy EXTERNAL_IMAGE_REFERENCES =
        UriReferencePolicy.allowing("http://", "https://");
    private static final UriReferencePolicy FILE_REFERENCES =
        UriReferencePolicy.allowing("file-api-");

    private DeepSeekImageInputs() {
    }

    static Map<String, Object> chatContentPart(Media media) {
        var fileId = fileId(media);
        if (fileId.isPresent()) {
            return Map.of("type", "file", "file_id", fileId.get());
        }
        var reference = imageReference(media);
        return Map.of("type", "image_url", "image_url", Map.of("url", reference));
    }

    static Map<String, Object> responsesContentPart(Media media) {
        var fileId = fileId(media);
        if (fileId.isPresent()) {
            return Map.of("type", "input_image", "file_id", fileId.get());
        }
        return Map.of("type", "input_image", "image_url", imageReference(media));
    }

    static Map<String, Object> messagesContentPart(Media media) {
        imageMimeType(media);
        var fileId = fileId(media);
        if (fileId.isPresent()) {
            return Map.of("type", "image", "source",
                Map.of("type", "file", "file_id", fileId.get()));
        }
        validateUrlReference(media);
        return Map.of("type", "image", "source",
            MediaContentSources.urlOrBase64Source(media, "DeepSeek Messages image"));
    }

    private static String imageReference(Media media) {
        var mime = imageMimeType(media);
        var data = media.getData();
        if (data instanceof byte[] bytes) {
            return dataUrl(mime, bytes);
        }
        if (data instanceof Resource resource) {
            return dataUrl(mime, readImage(resource));
        }
        var reference = data instanceof URI uri ? uri.toString() : stringValue(data);
        validateImageReference(reference);
        return reference;
    }

    private static Optional<String> fileId(Media media) {
        return MediaContentSources.urlReference(media)
            .filter(FILE_REFERENCES::allows);
    }

    private static void validateUrlReference(Media media) {
        MediaContentSources.urlReference(media)
            .filter(reference -> !FILE_REFERENCES.allows(reference))
            .ifPresent(DeepSeekImageInputs::validateImageReference);
    }

    private static String imageMimeType(Media media) {
        var mimeType = media.getMimeType();
        var mime = mimeType != null
            ? mimeType.toString().toLowerCase(Locale.ROOT)
            : MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
        if (IMAGE_MIME_TYPES.contains(mime)) {
            return mime;
        }
        throw new IllegalArgumentException(
            "DeepSeek image input supports JPEG, PNG, GIF, or WebP; received: " + mime);
    }

    private static void validateImageReference(String reference) {
        if (!IMAGE_REFERENCES.allows(reference)) {
            throw new IllegalArgumentException(
                "DeepSeek image input must be binary data, a data URL, or an HTTP(S) URL");
        }
        if (!EXTERNAL_IMAGE_REFERENCES.allows(reference)) {
            return;
        }
        if (reference.length() <= MAX_EXTERNAL_IMAGE_URL_LENGTH) {
            return;
        }
        throw new IllegalArgumentException(
            "DeepSeek external image URL must not exceed 8192 characters");
    }

    private static byte[] readImage(Resource resource) {
        try {
            return resource.getContentAsByteArray();
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Failed to read DeepSeek image content", error);
        }
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }
}
