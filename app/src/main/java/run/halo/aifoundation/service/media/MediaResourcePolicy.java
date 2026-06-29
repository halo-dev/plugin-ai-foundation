package run.halo.aifoundation.service.media;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.exception.MediaContentTooLargeException;
import run.halo.aifoundation.media.DataContent;

@Component
public class MediaResourcePolicy {

    static final long DEFAULT_MAX_PART_BYTES = 20L * 1024L * 1024L;
    static final long DEFAULT_MAX_TOTAL_BYTES = 80L * 1024L * 1024L;
    static final int DEFAULT_MAX_URL_COUNT = 16;
    static final int DEFAULT_MAX_URL_LENGTH = 4096;
    static final Set<String> DEFAULT_ALLOWED_URL_SCHEMES = Set.of("http", "https");

    private final long maxPartBytes;
    private final long maxTotalBytes;
    private final int maxUrlCount;
    private final int maxUrlLength;
    private final Set<String> allowedUrlSchemes;

    public MediaResourcePolicy() {
        this(DEFAULT_MAX_PART_BYTES, DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_URL_COUNT,
            DEFAULT_MAX_URL_LENGTH, DEFAULT_ALLOWED_URL_SCHEMES);
    }

    public MediaResourcePolicy(long maxPartBytes, long maxTotalBytes, int maxUrlCount,
        int maxUrlLength, Set<String> allowedUrlSchemes) {
        this.maxPartBytes = maxPartBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxUrlCount = maxUrlCount;
        this.maxUrlLength = maxUrlLength;
        this.allowedUrlSchemes = allowedUrlSchemes == null ? DEFAULT_ALLOWED_URL_SCHEMES
            : Set.copyOf(allowedUrlSchemes);
    }

    public void validate(List<DataContent> media) {
        if (media == null || media.isEmpty()) {
            return;
        }
        long totalBytes = 0;
        int urlCount = 0;
        for (int index = 0; index < media.size(); index++) {
            var item = media.get(index);
            if (item == null) {
                throw new InvalidMediaContentException("media content must not be null",
                    null, null, null, index);
            }
            if (item.isData()) {
                var bytes = item.decodedData().length;
                if (bytes > maxPartBytes) {
                    throw new MediaContentTooLargeException("part", maxPartBytes, bytes,
                        item.getMediaType(), item.getFilename(), null, index);
                }
                totalBytes += bytes;
                continue;
            }
            if (item.isUrl()) {
                validateUrl(item, index);
                urlCount++;
            }
        }
        if (totalBytes > maxTotalBytes) {
            throw new MediaContentTooLargeException("request", maxTotalBytes, totalBytes);
        }
        if (urlCount > maxUrlCount) {
            throw new InvalidMediaContentException("too many media URLs", null, null, null, null);
        }
    }

    private void validateUrl(DataContent item, int partIndex) {
        var url = item.getUrl();
        if (url.length() > maxUrlLength) {
            throw new InvalidMediaContentException("media URL exceeds maximum length",
                item.getMediaType(), item.getFilename(), null, partIndex);
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new InvalidMediaContentException("media URL must be a valid URI",
                item.getMediaType(), item.getFilename(), null, partIndex, e);
        }
        var scheme = uri.getScheme();
        if (scheme == null || !allowedUrlSchemes.contains(scheme.toLowerCase())) {
            throw new InvalidMediaContentException("media URL scheme is not supported",
                item.getMediaType(), item.getFilename(), null, partIndex);
        }
    }
}
