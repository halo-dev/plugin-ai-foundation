package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolRegistration;

/**
 * Verifies Brave Search readiness, configuration validation and execution-time credential lookup.
 *
 * <p>For example, a saved Secret reference enables discovery while a later VPN change is applied to the next search.
 */
class WebSearchSettingsTest {
    private static final String SEARCH_ID = "ai-foundation/web_search";
    private final ToolSettingsStore settings = mock(ToolSettingsStore.class);
    private final SecretResolver secrets = mock(SecretResolver.class);
    private final BraveWebSearcher webSearcher =
            spy(new BraveWebSearcher(mock(PublicHttpClient.class), new WebSearchSettings(settings, secrets)));

    @BeforeEach
    void setUp() {
        when(settings.configuration(SEARCH_ID)).thenReturn(Mono.just(Map.of()));
    }

    @Test
    void advertisesUnconfiguredSearchWithItsConfigurationAndResearchGroup() {
        var search = search();
        assertThat(search.isConfigured()).isFalse();
        assertThat(search.getConfiguration().getSchema()).hasSize(2);
        assertThat(search.getConfiguration().getSchema().getFirst()).containsEntry("$formkit", "secret");
        assertThat(search.getConfiguration().getSchema().getLast())
                .containsEntry("$formkit", "switch")
                .containsEntry("name", "vpnCompatibilityEnabled")
                .containsEntry("value", false);
        assertThat(search.getTags()).contains("联网搜索");
        assertThat(search.getGroup().getName()).isEqualTo("web");
        assertThat(search.getGroup().getPresets().getFirst().getName()).isEqualTo("research");
        assertThat(search.getGroup().getPresets().getFirst().getToolNames())
                .containsExactly("read_web_page", "web_search");
    }

    @Test
    void validSecretMakesSearchReadyWithoutExposingKeyInCatalog() {
        when(settings.configuration(SEARCH_ID)).thenReturn(Mono.just(Map.of("braveApiKeySecretName", "search-secret")));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.just("private-key"));
        var search = search();

        assertThat(search.isConfigured()).isTrue();
        assertThat(search.toString()).doesNotContain("private-key", "search-secret");
        assertThat(search.getConfiguration().getValues().get().block())
                .containsOnlyKeys("braveApiKeySecretName", "vpnCompatibilityEnabled")
                .containsEntry("braveApiKeySecretName", "search-secret")
                .containsEntry("vpnCompatibilityEnabled", false);
    }

    @Test
    void brokenConfigurationKeepsSearchVisibleAsUnconfigured() {
        when(settings.configuration(SEARCH_ID))
                .thenReturn(Mono.error(new IllegalStateException("ConfigMap unavailable")));
        assertThat(search().isConfigured()).isFalse();
    }

    @Test
    void hangingSecretKeepsTheConfigurableSearchCardVisible() {
        when(settings.configuration(SEARCH_ID)).thenReturn(Mono.just(Map.of("braveApiKeySecretName", "search-secret")));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.never());
        StepVerifier.withVirtualTime(webSearcher::tools)
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(2))
                .assertNext(tool -> {
                    assertThat(tool.getDefinition().getName()).isEqualTo("web_search");
                    assertThat(tool.isConfigured()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void savesOnlyValidatedSecretReferenceAndAllowsClearing() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        when(settings.configuration(SEARCH_ID)).thenAnswer(ignored -> Mono.just(stored.get()));
        when(settings.saveConfiguration(eq(SEARCH_ID), anyMap()))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> stored.set(invocation.getArgument(1))));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.just("private-key"));
        var configuration = search().getConfiguration();

        configuration
                .getSave()
                .apply(Map.of("braveApiKeySecretName", "search-secret"))
                .block();

        assertThat(stored.get())
                .containsOnlyKeys("braveApiKeySecretName", "vpnCompatibilityEnabled")
                .containsEntry("braveApiKeySecretName", "search-secret")
                .containsEntry("vpnCompatibilityEnabled", false);
        assertThat(stored.get().toString()).doesNotContain("private-key");
        assertThat(search().isConfigured()).isTrue();
        configuration.getSave().apply(Map.of("braveApiKeySecretName", " ")).block();
        assertThat(stored.get())
                .containsOnlyKeys("vpnCompatibilityEnabled")
                .containsEntry("vpnCompatibilityEnabled", false);
        assertThat(search().isConfigured()).isFalse();
    }

    @Test
    void rejectsRawKeysUnexpectedFieldsAndUnusableSecretsBeforeSaving() {
        var configuration = search().getConfiguration();
        StepVerifier.create(configuration.getSave().apply(Map.of("apiKey", "private-key")))
                .expectError(IllegalArgumentException.class)
                .verify();
        var nullKey = new HashMap<String, Object>();
        nullKey.put(null, "private-key");
        StepVerifier.create(configuration.getSave().apply(nullKey))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(configuration.getSave().apply(Map.of("braveApiKeySecretName", 42)))
                .expectError(IllegalArgumentException.class)
                .verify();
        for (var invalid : List.of("true", 1, List.of(true))) {
            StepVerifier.create(configuration.getSave().apply(Map.of("vpnCompatibilityEnabled", invalid)))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        when(secrets.resolveApiKey("missing"))
                .thenReturn(Mono.error(new IllegalArgumentException("Secret is missing")));
        StepVerifier.create(configuration.getSave().apply(Map.of("braveApiKeySecretName", "missing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        when(secrets.resolveApiKey("blank")).thenReturn(Mono.just(" "));
        StepVerifier.create(configuration.getSave().apply(Map.of("braveApiKeySecretName", "blank")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(settings, never()).saveConfiguration(eq(SEARCH_ID), anyMap());
    }

    @Test
    void emptySearchConfigurationValuesAlwaysExposeTheDisabledVpnDefault() {
        assertThat(search().getConfiguration().getValues().get().block())
                .containsOnlyKeys("vpnCompatibilityEnabled")
                .containsEntry("vpnCompatibilityEnabled", false);
    }

    @Test
    void savesVpnSeparatelyFromTheValidatedSecretAndNeverPersistsTheResolvedKey() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        when(settings.configuration(SEARCH_ID)).thenAnswer(ignored -> Mono.just(stored.get()));
        when(settings.saveConfiguration(eq(SEARCH_ID), anyMap()))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> stored.set(invocation.getArgument(1))));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.just("private-key"));
        var configuration = search().getConfiguration();
        configuration
                .getSave()
                .apply(Map.of("braveApiKeySecretName", "search-secret", "vpnCompatibilityEnabled", true))
                .block();
        assertThat(stored.get())
                .containsOnlyKeys("braveApiKeySecretName", "vpnCompatibilityEnabled")
                .containsEntry("braveApiKeySecretName", "search-secret")
                .containsEntry("vpnCompatibilityEnabled", true);
        assertThat(stored.get().toString()).doesNotContain("private-key");
        assertThat(configuration.getValues().get().block()).isEqualTo(stored.get());
        configuration.getSave().apply(Map.of("vpnCompatibilityEnabled", true)).block();
        assertThat(stored.get())
                .containsOnlyKeys("vpnCompatibilityEnabled")
                .containsEntry("vpnCompatibilityEnabled", true);
        assertThat(search().isConfigured()).isFalse();
    }

    @Test
    void defaultsSearchExecutionToVpnOffAndReadsTheCurrentFlagForEveryExecution() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of("braveApiKeySecretName", "search-secret"));
        when(settings.configuration(SEARCH_ID)).thenAnswer(ignored -> Mono.just(stored.get()));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.just("private-key"));
        var input = Map.<String, Object>of("query", "Halo");
        doReturn(Mono.just(Map.of("results", List.of()))).when(webSearcher).search(input, "private-key", false);
        doReturn(Mono.just(Map.of("results", List.of()))).when(webSearcher).search(input, "private-key", true);
        var registration = search();
        var context = ToolExecutionContext.builder()
                .toolName("web_search")
                .input(input)
                .build();
        StepVerifier.create(registration.getDefinition().getExecutor().execute(context))
                .expectNextCount(1)
                .verifyComplete();
        verify(webSearcher).search(input, "private-key", false);
        stored.set(Map.of("braveApiKeySecretName", "search-secret", "vpnCompatibilityEnabled", true));
        StepVerifier.create(registration.getDefinition().getExecutor().execute(context))
                .expectNextCount(1)
                .verifyComplete();
        verify(webSearcher).search(input, "private-key", true);
    }

    @Test
    void malformedSavedVpnConfigurationFailsClosedBeforeSearchExecution() {
        when(settings.configuration(SEARCH_ID))
                .thenReturn(
                        Mono.just(Map.of("braveApiKeySecretName", "search-secret", "vpnCompatibilityEnabled", "true")));
        var registration = search();
        assertThat(registration.isConfigured()).isFalse();
        StepVerifier.create(registration.getConfiguration().getValues().get())
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(registration
                        .getDefinition()
                        .getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .input(Map.of("query", "Halo"))
                                .build()))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(webSearcher, never()).search(anyMap(), anyString(), anyBoolean());
    }

    @Test
    void resolvedSearchOptionsNeverExposeCredentialsInDiagnostics() {
        var options = new WebSearchSettings.SearchOptions("private-key", true);

        assertThat(options.toString()).contains("<redacted>", "vpnCompatibilityEnabled=true");
        assertThat(options.toString()).doesNotContain("private-key");
    }

    private ToolRegistration search() {
        return webSearcher
                .tools()
                .filter(tool -> "web_search".equals(tool.getDefinition().getName()))
                .blockFirst();
    }
}
