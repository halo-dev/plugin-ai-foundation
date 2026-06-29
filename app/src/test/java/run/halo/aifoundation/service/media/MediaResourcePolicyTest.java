package run.halo.aifoundation.service.media;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.exception.MediaContentTooLargeException;
import run.halo.aifoundation.media.DataContent;

class MediaResourcePolicyTest {

    @Test
    void validate_usesDecodedDataSizeForPartAndTotalLimits() {
        var policy = new MediaResourcePolicy(4, 8, 2, 100, Set.of("https"));
        var fiveBytes = Base64.getEncoder()
            .encodeToString("12345".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> policy.validate(List.of(DataContent.data(fiveBytes,
            "image/png"))))
            .isInstanceOf(MediaContentTooLargeException.class)
            .extracting("scope", "maxBytes", "actualBytes")
            .containsExactly("part", 4L, 5L);
    }

    @Test
    void validate_checksTotalDecodedDataSize() {
        var policy = new MediaResourcePolicy(10, 8, 2, 100, Set.of("https"));
        var fiveBytes = Base64.getEncoder()
            .encodeToString("12345".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> policy.validate(List.of(
            DataContent.data(fiveBytes, "image/png"),
            DataContent.data(fiveBytes, "image/png")
        )))
            .isInstanceOf(MediaContentTooLargeException.class)
            .extracting("scope", "maxBytes", "actualBytes")
            .containsExactly("request", 8L, 10L);
    }

    @Test
    void validate_rejectsUnsupportedUrlSchemeAndLength() {
        var policy = new MediaResourcePolicy(10, 20, 2, 20, Set.of("https"));

        assertThatThrownBy(() -> policy.validate(List.of(DataContent.url("file:///tmp/a.png"))))
            .isInstanceOf(InvalidMediaContentException.class)
            .hasMessage("media URL scheme is not supported");
        assertThatThrownBy(() -> policy.validate(List.of(DataContent.url(
            "https://example.com/too-long.png"))))
            .isInstanceOf(InvalidMediaContentException.class)
            .hasMessage("media URL exceeds maximum length");
    }

    @Test
    void validate_rejectsTooManyUrls() {
        var policy = new MediaResourcePolicy(10, 20, 1, 100, Set.of("https"));

        assertThatThrownBy(() -> policy.validate(List.of(
            DataContent.url("https://example.com/a.png"),
            DataContent.url("https://example.com/b.png")
        )))
            .isInstanceOf(InvalidMediaContentException.class)
            .hasMessage("too many media URLs");
    }
}
