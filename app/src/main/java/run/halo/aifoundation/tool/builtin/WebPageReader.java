package run.halo.aifoundation.tool.builtin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WebPageReader {
    static final int MAX_RESPONSE_BYTES = 1_048_576;
    static final int MAX_CONTENT_CHARACTERS = 12_000;
    private final PublicHttpClient http;

    public Mono<Object> read(Map<String, Object> input) {
        return Mono.defer(() -> {
            var uri = URI.create(BuiltInToolInputs.requiredString(input, "url", 2_048));
            return http.get(uri, Map.of("Accept", "text/html,application/xhtml+xml,text/plain"),
                MAX_RESPONSE_BYTES, List.of(MediaType.TEXT_HTML,
                    MediaType.APPLICATION_XHTML_XML, MediaType.TEXT_PLAIN))
                .map(response -> extract(uri, response));
        });
    }

    static Map<String, Object> extract(URI uri, PublicHttpClient.Response response) {
        String title = "";
        String text;
        var charset = response.contentType().getCharset();
        if (MediaType.TEXT_PLAIN.isCompatibleWith(response.contentType())) {
            text = new String(response.body(), charset == null ? StandardCharsets.UTF_8 : charset);
        } else {
            try {
                var document = Jsoup.parse(new ByteArrayInputStream(response.body()),
                    charset == null ? null : charset.name(), uri.toString());
                document.select("script,style,noscript,iframe,object,embed,template,svg").remove();
                title = document.title();
                text = document.body() == null ? "" : document.body().text();
            } catch (IOException error) {
                throw new IllegalArgumentException("Unable to parse the web page");
            }
        }
        return Map.of("url", uri.toString(), "title", BuiltInToolInputs.truncate(title, 300),
            "content", BuiltInToolInputs.truncate(text, MAX_CONTENT_CHARACTERS),
            "truncated", text.length() > MAX_CONTENT_CHARACTERS);
    }
}
