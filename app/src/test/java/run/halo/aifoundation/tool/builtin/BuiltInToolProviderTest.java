package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolRegistration;

class BuiltInToolProviderTest {
    private static final String SEARCH_ID = "ai-foundation/web_search";
    private final ToolSettingsStore settings = mock(ToolSettingsStore.class);
    private final SecretResolver secrets = mock(SecretResolver.class);
    private BuiltInToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new BuiltInToolProvider(settings, secrets, mock(BraveWebSearcher.class),
            mock(WebPageReader.class), mock(SiteSearcher.class));
        when(settings.configuration(SEARCH_ID)).thenReturn(Mono.just(Map.of()));
    }

    @Test
    void advertisesUnconfiguredSearchAlongsideReadyTools() {
        StepVerifier.create(provider.tools().collectList()).assertNext(tools -> {
            assertThat(tools).extracting(tool -> tool.getDefinition().getName())
                .containsExactly("read_web_page", "search_site", "web_search");
            assertThat(tools).extracting(ToolRegistration::isConfigured)
                .containsExactly(true, true, false);
            assertThat(tools.getLast().getConfiguration().getSchema().getFirst())
                .containsEntry("$formkit", "secret");
            assertThat(tools.getLast().getTags()).contains("联网搜索");
        }).verifyComplete();
    }

    @Test
    void validSecretMakesSearchReadyWithoutExposingKeyInCatalog() {
        when(settings.configuration(SEARCH_ID))
            .thenReturn(Mono.just(Map.of("braveApiKeySecretName", "search-secret")));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.just("private-key"));
        var search = search();

        assertThat(search.isConfigured()).isTrue();
        assertThat(search.toString()).doesNotContain("private-key", "search-secret");
        assertThat(search.getConfiguration().getValues().get().block())
            .containsOnlyKeys("braveApiKeySecretName")
            .containsEntry("braveApiKeySecretName", "search-secret");
    }

    @Test
    void brokenConfigurationKeepsSearchVisibleAsUnconfigured() {
        when(settings.configuration(SEARCH_ID))
            .thenReturn(Mono.error(new IllegalStateException("ConfigMap unavailable")));
        assertThat(search().isConfigured()).isFalse();
    }

    @Test
    void hangingSecretCannotHideOtherToolsOrTheConfigurableCard() {
        when(settings.configuration(SEARCH_ID))
            .thenReturn(Mono.just(Map.of("braveApiKeySecretName", "search-secret")));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.never());
        StepVerifier.withVirtualTime(() -> provider.tools())
            .expectSubscription()
            .assertNext(tool -> assertThat(tool.getDefinition().getName()).isEqualTo("read_web_page"))
            .assertNext(tool -> assertThat(tool.getDefinition().getName()).isEqualTo("search_site"))
            .thenAwait(Duration.ofSeconds(2))
            .assertNext(tool -> {
                assertThat(tool.getDefinition().getName()).isEqualTo("web_search");
                assertThat(tool.isConfigured()).isFalse();
            }).verifyComplete();
    }

    @Test
    void savesOnlyValidatedSecretReferenceAndAllowsClearing() {
        var stored = new AtomicReference<Map<String, Object>>(Map.of());
        when(settings.configuration(SEARCH_ID)).thenAnswer(ignored -> Mono.just(stored.get()));
        when(settings.saveConfiguration(eq(SEARCH_ID), anyMap())).thenAnswer(invocation ->
            Mono.fromRunnable(() -> stored.set(invocation.getArgument(1))));
        when(secrets.resolveApiKey("search-secret")).thenReturn(Mono.just("private-key"));
        var configuration = search().getConfiguration();

        configuration.getSave().apply(Map.of("braveApiKeySecretName", "search-secret")).block();

        assertThat(stored.get()).containsOnlyKeys("braveApiKeySecretName")
            .containsEntry("braveApiKeySecretName", "search-secret");
        assertThat(stored.get().toString()).doesNotContain("private-key");
        assertThat(search().isConfigured()).isTrue();
        configuration.getSave().apply(Map.of("braveApiKeySecretName", " ")).block();
        assertThat(stored.get()).isEmpty();
        assertThat(search().isConfigured()).isFalse();
    }

    @Test
    void rejectsRawKeysUnexpectedFieldsAndUnusableSecretsBeforeSaving() {
        var configuration = search().getConfiguration();
        StepVerifier.create(configuration.getSave().apply(Map.of("apiKey", "private-key")))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(configuration.getSave().apply(Map.of("braveApiKeySecretName", 42)))
            .expectError(IllegalArgumentException.class).verify();
        when(secrets.resolveApiKey("missing")).thenReturn(Mono.error(
            new IllegalArgumentException("Secret is missing")));
        StepVerifier.create(configuration.getSave().apply(Map.of("braveApiKeySecretName", "missing")))
            .expectError(IllegalArgumentException.class).verify();
        when(secrets.resolveApiKey("blank")).thenReturn(Mono.just(" "));
        StepVerifier.create(configuration.getSave().apply(Map.of("braveApiKeySecretName", "blank")))
            .expectError(IllegalArgumentException.class).verify();
        verify(settings, never()).saveConfiguration(eq(SEARCH_ID), anyMap());
    }

    private ToolRegistration search() {
        return provider.tools().filter(tool -> "web_search".equals(tool.getDefinition().getName()))
            .blockFirst();
    }
}
