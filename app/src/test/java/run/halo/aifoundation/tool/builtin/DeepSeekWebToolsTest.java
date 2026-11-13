package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolConfiguration;
import run.halo.aifoundation.tool.ToolExecutionContext;

/**
 * Verifies model-facing input validation, credential boundaries and bounded DeepSeek tool output.
 *
 * <p>Transport correctness is exercised separately against real local HTTP servers. These tests cover the tool
 * contract, including Unicode query lengths and summaries remaining available before full bodies consume the budget.
 */
class DeepSeekWebToolsTest {
    private final DeepSeekWebToolSettings settings = mock(DeepSeekWebToolSettings.class);
    private final DeepSeekWebClient client = mock(DeepSeekWebClient.class);
    private final DeepSeekWebTools tools = new DeepSeekWebTools(settings, client);
    private final DeepSeekWebToolSettings.Options options = options(100_000, 10);

    @BeforeEach
    void setUp() {
        when(settings.readOptions(anyString())).thenReturn(Mono.just(options));
        when(settings.configured(anyString())).thenReturn(Mono.just(true));
        when(settings.configuration(anyString()))
                .thenReturn(Mono.just(ToolConfiguration.builder()
                        .schema(List.of(Map.of("$formkit", "secret", "name", "deepseekApiKeySecretName")))
                        .build()));
        when(client.search(any(), any())).thenReturn(Mono.just(Map.of("results", List.of())));
        when(client.fetch(any(), any())).thenReturn(Mono.just(Map.of("title", "Title", "content", "# Body")));
    }

    @Test
    void discoversBothReadOnlyToolsWithoutSendingNetworkRequests() {
        var registrations = tools.tools().collectList().block();
        assertThat(registrations).hasSize(2);
        assertThat(registrations)
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactly("deepseek_web_search", "deepseek_web_fetch");
        assertThat(registrations).allSatisfy(tool -> {
            assertThat(tool.getGroup()).isSameAs(HaloToolGroups.WEB);
            assertThat(tool.getTags()).contains("DeepSeek");
            assertThat(tool.getRequiredPermissions()).isEmpty();
            assertThat(tool.getDefinition().getApprovalPolicy()).isNull();
            assertThat(tool.getConfiguration().getSchema()).isNotEmpty();
        });
        verifyNoInteractions(client);
    }

    @Test
    void missingCredentialsLeaveBothToolsVisibleButUnconfigured() {
        when(settings.configured(anyString())).thenReturn(Mono.just(false));
        assertThat(tools.tools().collectList().block()).hasSize(2).allMatch(tool -> !tool.isConfigured());
        verifyNoInteractions(client);
    }

    @Test
    void searchPreservesOperatorsAndMapsOptionalArgumentsToTheOfficialApi() {
        var query = "Halo site:halo.run -site:example.com lang:zh after:2025";
        var input = Map.<String, Object>of(
                "query",
                query,
                "count",
                50,
                "oriQuestion",
                "如何使用 Halo？",
                "needContent",
                true,
                "localization",
                "2001:db8::1");
        StepVerifier.create(tools.search(input))
                .assertNext(value ->
                        assertThat(map(value)).containsEntry("query", query).containsEntry("truncated", false))
                .verifyComplete();
        verify(client)
                .search(
                        eq(options),
                        eq(Map.of(
                                "query",
                                query,
                                "count",
                                50,
                                "ori_question",
                                "如何使用 Halo？",
                                "need_content",
                                true,
                                "localization",
                                "2001:db8::1")));
    }

    @Test
    void absentCountUsesTheToolsConfiguredDefaultAndNullOriginalQuestionIsOmitted() {
        var configured = options(100_000, 37);
        when(settings.readOptions(DeepSeekWebToolSettings.SEARCH_TOOL_ID)).thenReturn(Mono.just(configured));
        var input = new LinkedHashMap<String, Object>();
        input.put("query", "Halo");
        input.put("oriQuestion", null);
        StepVerifier.create(tools.search(input)).expectNextCount(1).verifyComplete();
        verify(client).search(eq(configured), eq(Map.of("query", "Halo", "count", 37, "need_content", false)));
    }

    @Test
    void queryLengthUsesUnicodeCharactersRatherThanUtf16Units() {
        var query = "😀".repeat(128);
        StepVerifier.create(tools.search(Map.of("query", query)))
                .expectNextCount(1)
                .verifyComplete();
        verify(client).search(eq(options), eq(Map.of("query", query, "count", 10, "need_content", false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\u00a0", "\u2003"})
    void blankQueriesFailBeforeCredentialResolution(String query) {
        StepVerifier.create(tools.search(Map.of("query", query)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(settings, never()).readOptions(anyString());
        verifyNoInteractions(client);
    }

    @Test
    void oversizedUnicodeQueriesAndQuestionsAreRejected() {
        StepVerifier.create(tools.search(Map.of("query", "😀".repeat(129))))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(tools.search(Map.of("query", "Halo", "oriQuestion", "x".repeat(129))))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void malformedScalarArgumentsCannotReachTheApi() {
        for (var count : List.of(0, 51, 1.5, Double.NaN, "2", true)) {
            StepVerifier.create(tools.search(Map.of("query", "Halo", "count", count)))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(tools.search(Map.of("query", "Halo", "needContent", "true")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(tools.search(Map.of("query", "Halo", "localization", "example.com")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void modelArgumentsCannotChangeTheEndpointOrPassAnApiKey() {
        for (var field : List.of("providerName", "apiKey", "baseUrl", "limit")) {
            StepVerifier.create(tools.search(Map.of("query", "Halo", field, "other")))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(tools.fetch(Map.of("url", "https://example.com/", "providerName", "other")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void boundedSearchOutputKeepsAllSummariesBeforeAllocatingOptionalPageBodies() {
        var small = options(10, 10);
        when(settings.readOptions(DeepSeekWebToolSettings.SEARCH_TOOL_ID)).thenReturn(Mono.just(small));
        when(client.search(eq(small), any()))
                .thenReturn(Mono.just(Map.of("results", List.of(result("123456"), result("abcdef")))));
        var output =
                map(tools.search(Map.of("query", "Halo", "needContent", true)).block());
        var results = (List<?>) output.get("results");
        assertThat(results).hasSize(2);
        assertThat(map(results.getFirst()))
                .containsEntry("title", "A")
                .containsEntry("snippet", "B")
                .containsEntry("summary", "C")
                .containsEntry("content", "1234")
                .containsEntry("truncated", true)
                .containsEntry("url", "https://example.com/article")
                .containsEntry("site_name", null);
        assertThat(map(results.getLast()))
                .containsEntry("title", "A")
                .containsEntry("snippet", "B")
                .containsEntry("summary", "C")
                .containsEntry("content", "")
                .containsEntry("truncated", true);
        assertThat(output).containsEntry("truncated", true);
    }

    @Test
    void searchHonorsTheRequestedCountAndDoesNotReturnUnrequestedPageBodiesOrUnknownFields() {
        var item = result("A long body");
        item.put("unexpected", "Do not forward unknown response data");
        when(client.search(any(), any())).thenReturn(Mono.just(Map.of("results", List.of(item, item))));
        var output = map(tools.search(Map.of("query", "Halo", "count", 1)).block());
        var results = (List<?>) output.get("results");
        assertThat(results).hasSize(1);
        assertThat(map(results.getFirst()))
                .containsEntry("content", "")
                .containsEntry("truncated", false)
                .doesNotContainKey("unexpected");
        assertThat(output).containsEntry("truncated", false);
    }

    @Test
    void fetchReturnsBoundedMarkdownWithoutSplittingUnicodeSurrogatePairs() {
        when(settings.readOptions(DeepSeekWebToolSettings.FETCH_TOOL_ID)).thenReturn(Mono.just(options(4, 10)));
        when(client.fetch(any(), any())).thenReturn(Mono.just(Map.of("title", "Ab", "content", "😀xyz")));
        StepVerifier.create(tools.fetch(Map.of("url", "https://example.com/a?q=1#heading")))
                .assertNext(value -> assertThat(map(value))
                        .containsEntry("url", "https://example.com/a?q=1#heading")
                        .containsEntry("title", "Ab")
                        .containsEntry("content", "😀x")
                        .containsEntry("format", "markdown")
                        .containsEntry("truncated", true))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ftp://example.com/a",
                "file:///tmp/a",
                "//example.com/a",
                "/a",
                "https://",
                "https://user:password@example.com/a",
                "https://example.com:0",
                "https://example.com:65536"
            })
    void fetchRejectsInvalidOrCredentialBearingUrlsBeforeAnyApiRequest(String url) {
        StepVerifier.create(tools.fetch(Map.of("url", url)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(settings, never()).readOptions(anyString());
        verifyNoInteractions(client);
    }

    @Test
    void fetchEnforcesTheDocumentedUrlLimit() {
        var accepted = "https://example.com/" + "a".repeat(8192 - "https://example.com/".length());
        StepVerifier.create(tools.fetch(Map.of("url", accepted)))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(tools.fetch(Map.of("url", accepted + "a")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void retainedExecutorsReadCurrentSettingsAndRejectUnavailableCredentials() {
        var registrations = tools.tools().collectList().block();
        when(settings.readOptions(DeepSeekWebToolSettings.SEARCH_TOOL_ID)).thenReturn(Mono.empty());
        StepVerifier.create(registrations
                        .getFirst()
                        .getDefinition()
                        .getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .input(Map.of("query", "Halo"))
                                .build()))
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).contains("DeepSeek API Key"))
                .verify();
        verifyNoInteractions(client);
    }

    private static Map<String, Object> result(String content) {
        var result = new LinkedHashMap<String, Object>();
        result.put("title", "A");
        result.put("url", "https://example.com/article");
        result.put("snippet", "B");
        result.put("summary", "C");
        result.put("content", content);
        result.put("rerank_score", 2.4);
        result.put("authority", 3);
        result.put("site_name", null);
        result.put("site_icon", null);
        result.put("date", null);
        return result;
    }

    private static DeepSeekWebToolSettings.Options options(int textLimit, int defaultCount) {
        return new DeepSeekWebToolSettings.Options(
                "test-api-key", 10_485_760, textLimit, Duration.ofSeconds(30), defaultCount);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
