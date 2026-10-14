package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class WebPageReaderTest {
    @Test
    void extractsPlainTextAndRemovesExecutableAndEmbeddedContent() {
        var html = """
            <!doctype html><html><head><title>Halo 文档</title><style>hidden-css</style></head>
            <body><h1>公开内容</h1><script>secret-script</script><noscript>tracking</noscript>
            <iframe>embedded-secret</iframe><template>template-secret</template><p>A &amp; B</p>
            </body></html>
            """;
        var result = WebPageReader.extract(URI.create("https://example.com/"),
            new PublicHttpClient.Response(html.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_HTML));
        assertThat(result).containsEntry("title", "Halo 文档")
            .containsEntry("content", "公开内容 A & B").containsEntry("truncated", false);
    }

    @Test
    void truncatesLongResponsesAndSignalsTruncation() {
        var text = "a".repeat(WebPageReader.MAX_CONTENT_CHARACTERS + 1);
        var result = WebPageReader.extract(URI.create("https://example.com/"),
            new PublicHttpClient.Response(text.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));
        assertThat((String) result.get("content")).hasSize(WebPageReader.MAX_CONTENT_CHARACTERS);
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    void appliesTransportResponseLimitAndDoesNotSendCallerCredentials() {
        var http = mock(PublicHttpClient.class);
        when(http.get(any(), any(), anyInt(), any())).thenReturn(Mono.just(
            new PublicHttpClient.Response("Hello".getBytes(StandardCharsets.UTF_8),
                MediaType.TEXT_PLAIN)));
        StepVerifier.create(new WebPageReader(http).read(Map.of("url", "https://example.com/")))
            .assertNext(result -> assertThat(((Map<?, ?>) result).get("content")).isEqualTo("Hello"))
            .verifyComplete();
        verify(http).get(URI.create("https://example.com/"),
            Map.of("Accept", "text/html,application/xhtml+xml,text/plain"),
            WebPageReader.MAX_RESPONSE_BYTES,
            java.util.List.of(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML,
                MediaType.TEXT_PLAIN));
    }
}
