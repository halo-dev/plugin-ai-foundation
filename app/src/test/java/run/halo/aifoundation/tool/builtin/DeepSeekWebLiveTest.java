package run.halo.aifoundation.tool.builtin;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

/**
 * Exercises DeepSeek's official APIs through the real shared tools and Secret resolution path.
 *
 * <p>This optional test makes exactly three API calls: search without page bodies, search with page bodies, and fetch
 * of Halo's official website. It is disabled unless {@code AI_FOUNDATION_DEEPSEEK_LIVE_KEY_FILE} names a regular,
 * non-symlink UTF-8 file with mode {@code 600} containing the API key. Successful calls may be billed by DeepSeek.
 * Assertions and console summaries never include credentials or raw responses.
 *
 * <pre>{@code
 * // Set AI_FOUNDATION_DEEPSEEK_LIVE_KEY_FILE to an existing protected key file, then run:
 * // ./gradlew :app:test --tests '*DeepSeekWebLiveTest' --rerun-tasks
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "AI_FOUNDATION_DEEPSEEK_LIVE_KEY_FILE", matches = ".+")
class DeepSeekWebLiveTest {
    private static final String SECRET_NAME = "deepseek-live-api-key";
    private static final String QUERY = "Halo CMS 官方网站 site:halo.run";
    private static final String PAGE_URL = "https://www.halo.run/";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(35);

    @Test
    void searchesAndFetchesRealHaloWebContentUsingTheConfiguredSecret() {
        var store = mock(ToolSettingsStore.class);
        var extensions = mock(ReactiveExtensionClient.class);
        var stored = new HashMap<String, Map<String, Object>>();
        var secret = new Secret();
        secret.setStringData(Map.of("api-key", readProtectedKey()));
        when(extensions.fetch(Secret.class, SECRET_NAME)).thenReturn(Mono.just(secret));
        when(store.configuration(anyString()))
                .thenAnswer(invocation -> Mono.defer(() -> Mono.justOrEmpty(stored.get(invocation.getArgument(0)))));
        when(store.saveConfiguration(anyString(), anyMap()))
                .thenAnswer(invocation ->
                        Mono.fromRunnable(() -> stored.put(invocation.getArgument(0), invocation.getArgument(1))));

        var settings = new DeepSeekWebToolSettings(store, new SecretResolver(extensions));
        configure(settings, DeepSeekWebToolSettings.SEARCH_TOOL_ID);
        configure(settings, DeepSeekWebToolSettings.FETCH_TOOL_ID);
        var tools = new DeepSeekWebTools(settings, new DeepSeekWebClient());

        var summaries = search(tools, false);
        var pages = search(tools, true);
        var extracted = asMap(tools.fetch(Map.of("url", PAGE_URL)).block(CALL_TIMEOUT));
        check("markdown".equals(extracted.get("format")), "Fetch must identify the extracted format as Markdown");
        check(PAGE_URL.equals(extracted.get("url")), "Fetch must preserve the requested source URL");
        var content = text(extracted, "content");
        check(!content.isBlank(), "Halo's official homepage must have readable extracted content");
        text(extracted, "title");
        System.out.println("DeepSeek live: summary results=" + summaries.size()
                + ", results with page bodies="
                + pages.stream().filter(DeepSeekWebLiveTest::hasBody).count()
                + ", fetched Markdown characters=" + content.codePointCount(0, content.length()));
    }

    private static List<Map<?, ?>> search(DeepSeekWebTools tools, boolean needContent) {
        var output = asMap(tools.search(Map.of("query", QUERY, "count", 2, "needContent", needContent))
                .block(CALL_TIMEOUT));
        check(QUERY.equals(output.get("query")), "Search must preserve its query");
        check(output.get("results") instanceof List<?>, "Search must return a result array");
        var raw = (List<?>) output.get("results");
        check(!raw.isEmpty() && raw.size() <= 2, "Halo search must return one or two matching results");
        var results = raw.stream().map(DeepSeekWebLiveTest::asMap).toList();
        for (var result : results) {
            text(result, "title");
            check(!text(result, "url").isBlank(), "Each real search result must have a URL");
            check(result.get("rerank_score") instanceof Number, "Each real search result must have a ranking score");
            check(result.get("authority") instanceof Number, "Each real search result must have authority metadata");
            check(
                    result.containsKey("site_name") && result.containsKey("site_icon") && result.containsKey("date"),
                    "Search must preserve nullable site metadata");
            if (!needContent) {
                check(text(result, "content").isEmpty(), "Search without page bodies must return empty content");
            }
        }
        if (needContent) {
            check(
                    results.stream().anyMatch(DeepSeekWebLiveTest::hasBody),
                    "Search with page bodies must extract content for at least one Halo result");
        }
        System.out.println("DeepSeek live search: needContent=" + needContent + ", results=" + results.size()
                + ", truncated=" + output.get("truncated") + ", text lengths="
                + results.stream()
                        .map(result -> Map.of(
                                "title", text(result, "title").length(),
                                "snippet", text(result, "snippet").length(),
                                "summary", text(result, "summary").length(),
                                "content", text(result, "content").length()))
                        .toList());
        return results;
    }

    private static boolean hasBody(Map<?, ?> result) {
        return result.get("content") instanceof String content && !content.isBlank();
    }

    private static void configure(DeepSeekWebToolSettings settings, String id) {
        var values = new LinkedHashMap<String, Object>();
        values.put("deepseekApiKeySecretName", SECRET_NAME);
        if (id.equals(DeepSeekWebToolSettings.SEARCH_TOOL_ID)) {
            values.put("defaultCount", 2);
        }
        var configuration = settings.configuration(id).block();
        check(configuration != null, "The real tool settings must expose a configuration form");
        configuration.getSave().apply(values).block();
    }

    private static String readProtectedKey() {
        try {
            var path = Path.of(System.getenv("AI_FOUNDATION_DEEPSEEK_LIVE_KEY_FILE"));
            check(
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                    "The live key must be stored in a regular file without a symbolic link");
            check(
                    Files.getPosixFilePermissions(path)
                            .equals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)),
                    "The live key file must have mode 600");
            check(Files.size(path) <= 16_384, "The live key file is unexpectedly large");
            var key = Files.readString(path).strip();
            check(
                    !key.isBlank() && !key.contains("\n") && !key.contains("\r"),
                    "The live key file must contain one nonblank API key");
            return key;
        } catch (IOException error) {
            throw new AssertionError("The protected live API key file could not be read");
        }
    }

    private static Map<?, ?> asMap(Object value) {
        check(value instanceof Map<?, ?>, "The real tool must return a structured response");
        return (Map<?, ?>) value;
    }

    private static String text(Map<?, ?> value, String field) {
        check(value.get(field) instanceof String, "The real tool response is missing a required text field");
        return (String) value.get(field);
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new AssertionError(message);
        }
    }
}
