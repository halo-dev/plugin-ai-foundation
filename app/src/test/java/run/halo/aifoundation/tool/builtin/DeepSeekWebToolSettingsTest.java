package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolConfiguration;

/**
 * Verifies independent DeepSeek tool limits, standard Halo Secret configuration and credential isolation.
 *
 * <p>For example, both tools can select the same existing Secret, and rotating its key affects their next execution.
 * Configuration responses contain only the Secret name and tool limits; plain-text credentials are rejected.
 */
class DeepSeekWebToolSettingsTest {
    private static final String SEARCH = DeepSeekWebToolSettings.SEARCH_TOOL_ID;
    private static final String FETCH = DeepSeekWebToolSettings.FETCH_TOOL_ID;
    private static final String SECRET = "deepseekApiKeySecretName";
    private final ToolSettingsStore store = mock(ToolSettingsStore.class);
    private final SecretResolver secrets = mock(SecretResolver.class);
    private final Map<String, Map<String, Object>> stored = new HashMap<>();
    private final Map<String, String> keys = new HashMap<>();
    private DeepSeekWebToolSettings settings;

    @BeforeEach
    void setUp() {
        settings = new DeepSeekWebToolSettings(store, secrets);
        when(store.configuration(anyString()))
                .thenAnswer(invocation -> Mono.defer(() -> Mono.justOrEmpty(stored.get(invocation.getArgument(0)))));
        when(store.saveConfiguration(anyString(), anyMap()))
                .thenAnswer(invocation ->
                        Mono.fromRunnable(() -> stored.put(invocation.getArgument(0), invocation.getArgument(1))));
        when(secrets.resolveApiKey(anyString()))
                .thenAnswer(invocation -> Mono.defer(() -> {
                    var key = keys.get(invocation.getArgument(0));
                    if (key == null) {
                        return Mono.error(new IllegalArgumentException("Secret is not available"));
                    }
                    return Mono.just(key);
                }));
        keys.put("deepseek-secret", "resolved-api-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {SEARCH, FETCH})
    void exposesTheStandardSecretInputWithoutCredentialLookup(String toolId) {
        var form = configuration(toolId);
        var secret = form.getSchema().getFirst();
        assertThat(secret)
                .containsEntry("$formkit", "secret")
                .containsEntry("name", SECRET)
                .containsEntry("label", "DeepSeek API Key");
        assertThat(secret.get("requiredKeys")).isEqualTo(List.of(Map.of("key", "api-key", "help", "DeepSeek API Key")));
        assertThat(form.getSchema().stream().map(node -> node.get("name")))
                .doesNotContain("providerName", "baseUrl", "proxyHost", "apiKey");
        var resource = toolId.equals(SEARCH) ? "deepseek-web-search" : "deepseek-web-fetch";
        assertThat(form.getSchema()).isSameAs(ToolConfigurationSchemas.load(resource));
        assertThatThrownBy(secret::clear).isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(store, secrets);
    }

    @ParameterizedTest
    @ValueSource(strings = {SEARCH, FETCH})
    void keepsUnconfiguredToolsVisibleWithIndependentDefaults(String toolId) {
        var values = configuration(toolId).getValues().get().block();
        assertThat(values).containsAllEntriesOf(defaults());
        if (toolId.equals(SEARCH)) {
            assertThat(values).containsEntry("defaultCount", 10).hasSize(4);
        } else {
            assertThat(values).hasSize(3).doesNotContainKey("defaultCount");
        }
        assertThat(values).doesNotContainKey(SECRET);
        StepVerifier.create(settings.readOptions(toolId)).verifyComplete();
        assertThat(settings.configured(toolId).block()).isFalse();
        verify(secrets, never()).resolveApiKey(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {SEARCH, FETCH})
    void resolvesTheSelectedSecretWithoutPersistingItsCredential(String toolId) {
        var form = configuration(toolId);
        form.getSave().apply(Map.of(SECRET, " deepseek-secret ")).block();

        var options = settings.readOptions(toolId).block();
        assertThat(options.apiKey()).isEqualTo("resolved-api-key");
        assertThat(options.maxResponseBytes()).isEqualTo(10 * 1_048_576);
        assertThat(options.maxContentCharacters()).isEqualTo(100_000);
        assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.defaultCount()).isEqualTo(10);
        assertThat(settings.configured(toolId).block()).isTrue();
        assertThat(stored.get(toolId)).containsEntry(SECRET, "deepseek-secret");
        assertThat(stored.get(toolId).toString()).doesNotContain("resolved-api-key");
        assertThat(form.getValues().get().block().toString()).doesNotContain("resolved-api-key");
        assertThat(options.toString()).contains("<redacted>").doesNotContain("resolved-api-key", "deepseek-secret");
        verify(store, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void reusesAnExistingSecretWhileKeepingBothToolConfigurationsIndependent() {
        configuration(SEARCH)
                .getSave()
                .apply(Map.of(SECRET, "deepseek-secret", "defaultCount", 25))
                .block();
        configuration(FETCH)
                .getSave()
                .apply(Map.of(SECRET, "deepseek-secret", "maxContentCharacters", 500_000))
                .block();

        assertThat(settings.readOptions(SEARCH).block().defaultCount()).isEqualTo(25);
        assertThat(settings.readOptions(SEARCH).block().maxContentCharacters()).isEqualTo(100_000);
        assertThat(settings.readOptions(FETCH).block().maxContentCharacters()).isEqualTo(500_000);
        assertThat(settings.readOptions(FETCH).block().apiKey()).isEqualTo("resolved-api-key");
        keys.put("deepseek-secret", "rotated-shared-key");
        assertThat(settings.readOptions(SEARCH).block().apiKey()).isEqualTo("rotated-shared-key");
        assertThat(settings.readOptions(FETCH).block().apiKey()).isEqualTo("rotated-shared-key");
        configuration(FETCH).getSave().apply(Map.of()).block();
        assertThat(settings.configured(FETCH).block()).isFalse();
        assertThat(settings.configured(SEARCH).block()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void clearingTheSecretRestoresAnUnconfiguredTool(String secretName) {
        configuration(SEARCH).getSave().apply(Map.of(SECRET, "deepseek-secret")).block();
        var submitted = new HashMap<String, Object>();
        submitted.put(SECRET, secretName);
        configuration(SEARCH).getSave().apply(submitted).block();

        assertThat(stored.get(SEARCH)).doesNotContainKey(SECRET);
        assertThat(settings.configured(SEARCH).block()).isFalse();
        StepVerifier.create(settings.readOptions(SEARCH)).verifyComplete();
    }

    @Test
    void resolvesSecretRotationAndDeletedSecretsOnEachSubscription() {
        stored.put(FETCH, Map.of(SECRET, "deepseek-secret"));
        var options = settings.readOptions(FETCH);
        assertThat(options.block().apiKey()).isEqualTo("resolved-api-key");

        keys.put("deepseek-secret", "rotated-key");
        assertThat(options.block().apiKey()).isEqualTo("rotated-key");
        stored.put(FETCH, Map.of(SECRET, "replacement-secret"));
        keys.put("replacement-secret", "replacement-key");
        assertThat(options.block().apiKey()).isEqualTo("replacement-key");
        keys.remove("replacement-secret");
        assertThat(settings.configured(FETCH).block()).isFalse();
        StepVerifier.create(options).expectError(IllegalArgumentException.class).verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty-key", "blank-key", "empty-publisher"})
    void refusesToSaveMissingOrEmptyCredentials(String scenario) {
        switch (scenario) {
            case "missing" -> keys.clear();
            case "empty-key" -> keys.put("deepseek-secret", "");
            case "blank-key" -> keys.put("deepseek-secret", " ");
            case "empty-publisher" ->
                when(secrets.resolveApiKey("deepseek-secret")).thenReturn(Mono.empty());
            default -> throw new IllegalArgumentException(scenario);
        }
        StepVerifier.create(configuration(SEARCH).getSave().apply(Map.of(SECRET, "deepseek-secret")))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(stored).doesNotContainKey(SEARCH);
        stored.put(SEARCH, Map.of(SECRET, "deepseek-secret"));
        assertThat(settings.configured(SEARCH).block()).isFalse();
    }

    @Test
    void canonicalizesIntegralNumbersAndSupportsAllInclusiveBoundaries() {
        var form = configuration(SEARCH);
        form.getSave()
                .apply(Map.of(
                        SECRET,
                        "deepseek-secret",
                        "maxResponseSizeMb",
                        1L,
                        "maxContentCharacters",
                        1,
                        "timeoutSeconds",
                        new BigDecimal("1.000"),
                        "defaultCount",
                        1))
                .block();
        var minimum = settings.readOptions(SEARCH).block();
        assertThat(minimum.maxResponseBytes()).isEqualTo(1_048_576);
        assertThat(minimum.maxContentCharacters()).isEqualTo(1);
        assertThat(minimum.timeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(minimum.defaultCount()).isEqualTo(1);
        form.getSave()
                .apply(Map.of(
                        SECRET,
                        "deepseek-secret",
                        "maxResponseSizeMb",
                        100,
                        "maxContentCharacters",
                        2_000_000,
                        "timeoutSeconds",
                        120,
                        "defaultCount",
                        50))
                .block();
        var maximum = settings.readOptions(SEARCH).block();
        assertThat(maximum.maxResponseBytes()).isEqualTo(104_857_600);
        assertThat(maximum.maxContentCharacters()).isEqualTo(2_000_000);
        assertThat(maximum.timeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(maximum.defaultCount()).isEqualTo(50);
        assertThat(stored.get(SEARCH).get("maxResponseSizeMb")).isInstanceOf(Integer.class);
        assertThat(stored.get(SEARCH).get("timeoutSeconds")).isInstanceOf(Integer.class);
    }

    @ParameterizedTest
    @MethodSource("invalidConfiguration")
    void rejectsMalformedFieldsAndLimitsWithoutSaving(String toolId, Map<String, Object> submitted) {
        StepVerifier.create(configuration(toolId).getSave().apply(submitted))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(stored).doesNotContainKey(toolId);
    }

    private static Stream<Arguments> invalidConfiguration() {
        var invalidLimits = Stream.of("maxResponseSizeMb", "maxContentCharacters", "timeoutSeconds", "defaultCount")
                .flatMap(field -> Stream.of(
                                null, "10", true, 0, -1, 2_000_001, 1.5, Double.NaN, Double.POSITIVE_INFINITY)
                        .map(value -> {
                            var values = new HashMap<String, Object>();
                            values.put(field, value);
                            return Arguments.of(SEARCH, values);
                        }));
        var unsupported = new HashMap<String, Object>();
        unsupported.put(null, "value");
        return Stream.concat(
                invalidLimits,
                Stream.of(
                        Arguments.of(SEARCH, null),
                        Arguments.of(SEARCH, unsupported),
                        Arguments.of(SEARCH, Map.of("apiKey", "must-not-save")),
                        Arguments.of(SEARCH, Map.of("providerName", "deepseek-main")),
                        Arguments.of(SEARCH, Map.of("baseUrl", "https://example.com")),
                        Arguments.of(SEARCH, Map.of("proxyHost", "proxy.example")),
                        Arguments.of(SEARCH, Map.of(SECRET, true)),
                        Arguments.of(FETCH, Map.of("defaultCount", 10)),
                        Arguments.of(SEARCH, Map.of("maxResponseSizeMb", 101)),
                        Arguments.of(SEARCH, Map.of("timeoutSeconds", 121)),
                        Arguments.of(SEARCH, Map.of("defaultCount", 51))));
    }

    @Test
    void treatsConfigurationFailuresAndCredentialTimeoutsAsUnconfigured() {
        when(store.configuration(SEARCH)).thenReturn(Mono.error(new IllegalStateException("store unavailable")));
        assertThat(settings.configured(SEARCH).block()).isFalse();
        when(store.configuration(SEARCH)).thenReturn(Mono.just(Map.of(SECRET, "deepseek-secret")));
        when(secrets.resolveApiKey("deepseek-secret")).thenReturn(Mono.never());
        StepVerifier.withVirtualTime(() -> settings.configured(SEARCH))
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void rejectsInvalidStoredSettingsBeforeResolvingTheSecret() {
        stored.put(SEARCH, Map.of(SECRET, "deepseek-secret", "maxResponseSizeMb", "100"));
        StepVerifier.create(settings.readOptions(SEARCH))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(settings.configured(SEARCH).block()).isFalse();
        verify(secrets, never()).resolveApiKey(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ai-foundation/web_search", "deepseek_web_search"})
    void rejectsUnknownToolIds(String toolId) {
        StepVerifier.create(settings.configuration(toolId))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(settings.readOptions(toolId))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private ToolConfiguration configuration(String toolId) {
        return settings.configuration(toolId).block();
    }

    private static Map<String, Object> defaults() {
        return Map.of("maxResponseSizeMb", 10, "maxContentCharacters", 100_000, "timeoutSeconds", 30);
    }
}
