package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import run.halo.aifoundation.media.DataContent;

class MediaContentSourcesTest {

    @Test
    void keepsTheProviderRequestedDataRepresentationExplicit() {
        var image = DataContent.data(new byte[] {1, 2, 3}, "image/png");

        assertThat(MediaContentSources.urlOrDataUrl(image, "image"))
            .startsWith("data:image/png;base64,");
        assertThat(MediaContentSources.urlOrBase64(image, "image"))
            .isEqualTo(image.getData());
        assertThat(MediaContentSources.urlOrDataUrl(
            DataContent.url("https://example.com/image.png"), "image"))
            .isEqualTo("https://example.com/image.png");
    }

    @Test
    void normalizesSpringMediaWithoutChoosingAProviderWireShape() {
        var url = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(URI.create("https://example.com/image.png"))
            .build();
        var bytes = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1, 2, 3})
            .build();

        assertThat(MediaContentSources.urlReference(url))
            .contains("https://example.com/image.png");
        assertThat(MediaContentSources.urlOrDataUrl(url, "image"))
            .isEqualTo("https://example.com/image.png");
        assertThat(MediaContentSources.urlReference(bytes)).isEmpty();
        assertThat(MediaContentSources.urlOrDataUrl(bytes, "image"))
            .startsWith("data:image/png;base64,");
        assertThat(MediaContentSources.rawBase64(bytes, "image")).isEqualTo("AQID");
    }

    @Test
    void buildsMessagesUrlOrBase64SourcesWithoutTreatingDataUrlsAsRemoteUrls() {
        var remote = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(URI.create("https://example.com/image.png"))
            .build();
        var dataUrl = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data("data:image/png;base64,AQID")
            .build();

        assertThat(MediaContentSources.urlOrBase64Source(remote, "image"))
            .containsEntry("type", "url")
            .containsEntry("url", "https://example.com/image.png");
        assertThat(MediaContentSources.urlOrBase64Source(dataUrl, "image"))
            .containsEntry("type", "base64")
            .containsEntry("media_type", "image/png")
            .containsEntry("data", "AQID");

        var unsupported = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
            .data("file:///tmp/image.png")
            .build();
        assertThatThrownBy(() ->
            MediaContentSources.urlOrBase64Source(unsupported, "image"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTP(S)");
    }
}
