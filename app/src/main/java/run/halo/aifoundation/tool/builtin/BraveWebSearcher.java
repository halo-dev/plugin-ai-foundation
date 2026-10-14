package run.halo.aifoundation.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class BraveWebSearcher {
    private static final String ENDPOINT = "https://api.search.brave.com/res/v1/web/search";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final PublicHttpClient http;

    public Mono<Object> search(Map<String, Object> input, String apiKey) {
        return Mono.defer(() -> {
            if (apiKey == null || apiKey.isBlank()) {
                return Mono.error(new IllegalStateException("Configure the Brave Search API Key "
                    + "in AI Foundation plugin settings before using web search"));
            }
            var query = BuiltInToolInputs.requiredString(input, "query", 500);
            var limit = BuiltInToolInputs.limit(input);
            var uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("q", "{query}").queryParam("count", limit)
                .queryParam("text_decorations", false)
                .encode().buildAndExpand(query).toUri();
            return http.get(uri, Map.of("Accept", "application/json",
                    "X-Subscription-Token", apiKey), 262_144, List.of(MediaType.APPLICATION_JSON))
                .map(response -> parse(query, limit, response.body()));
        });
    }

    static Map<String, Object> parse(String query, int limit, byte[] body) {
        try {
            var root = JSON.readTree(body);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Invalid Brave Search response");
            }
            var webResults = root.path("web").path("results");
            var results = new ArrayList<Map<String, Object>>();
            if (webResults.isArray()) {
                for (var result : webResults) {
                    if (results.size() >= limit) {
                        break;
                    }
                    var url = result.path("url").asText("");
                    if (url.isBlank()) {
                        continue;
                    }
                    results.add(Map.of("title", plain(result.path("title").asText(""), 300),
                        "url", BuiltInToolInputs.truncate(url, 2_048),
                        "description", plain(result.path("description").asText(""), 1_000)));
                }
            }
            return Map.of("query", query, "provider", "brave", "results", results);
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid Brave Search response");
        }
    }

    private static String plain(String value, int limit) {
        return BuiltInToolInputs.truncate(Jsoup.parse(value).text(), limit);
    }
}
