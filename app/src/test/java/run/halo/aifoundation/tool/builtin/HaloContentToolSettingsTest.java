package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.service.tool.ToolSettingsStore;

class HaloContentToolSettingsTest {
    private static final String FIELD = "maxContentCharacters";
    private final ToolSettingsStore store = mock(ToolSettingsStore.class);
    private final Map<String, Map<String, Object>> stored = new HashMap<>();
    private final HaloContentToolSettings settings = new HaloContentToolSettings(store);

    @BeforeEach
    void setUp() {
        when(store.configuration(anyString()))
                .thenAnswer(invocation ->
                        Mono.fromSupplier(() -> stored.getOrDefault(invocation.getArgument(0), Map.of())));
        when(store.saveConfiguration(anyString(), anyMap())).thenAnswer(invocation -> {
            var id = invocation.<String>getArgument(0);
            var values = invocation.<Map<String, Object>>getArgument(1);
            return Mono.fromRunnable(() -> stored.put(id, values));
        });
    }

    @Test
    void exposesANativeIntegerFieldAndDefaultsForEachTool() {
        for (var localName : List.of("get_post", "get_page")) {
            var configuration = settings.configuration(localName);
            assertThat(configuration.getSchema()).hasSize(1);
            assertThat(configuration.getSchema().getFirst())
                    .containsEntry("$formkit", "number")
                    .containsEntry("name", FIELD)
                    .containsEntry("number", "integer")
                    .containsEntry("min", 1)
                    .containsEntry("max", 2_000_000);
            StepVerifier.create(configuration.getValues().get())
                    .assertNext(values -> assertThat(values).containsExactlyEntriesOf(Map.of(FIELD, 100_000)))
                    .verifyComplete();
        }
    }

    @Test
    void savesIndependentPerToolLimitsWithoutChangingEnabledStates() {
        settings.configuration("get_post").getSave().apply(Map.of(FIELD, 1)).block();
        settings.configuration("get_page")
                .getSave()
                .apply(Map.of(FIELD, 2_000_000L))
                .block();
        assertThat(stored.get("ai-foundation/get_post")).containsExactlyEntriesOf(Map.of(FIELD, 1));
        assertThat(stored.get("ai-foundation/get_page")).containsExactlyEntriesOf(Map.of(FIELD, 2_000_000));
        assertThat(settings.maxContentCharacters("get_post").block()).isEqualTo(1);
        assertThat(settings.maxContentCharacters("get_page").block()).isEqualTo(2_000_000);
        verify(store, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void readsTheLatestConfigurationForEverySubscription() {
        var limit = settings.maxContentCharacters("get_post");
        assertThat(limit.block()).isEqualTo(100_000);
        stored.put("ai-foundation/get_post", Map.of(FIELD, 21));
        assertThat(limit.block()).isEqualTo(21);
        stored.put("ai-foundation/get_post", Map.of(FIELD, 7));
        assertThat(limit.block()).isEqualTo(7);
    }

    @Test
    void emptySaveRestoresOnlyTheSelectedToolsDefault() {
        stored.put("ai-foundation/get_post", Map.of(FIELD, 8));
        stored.put("ai-foundation/get_page", Map.of(FIELD, 16));
        settings.configuration("get_post").getSave().apply(Map.of()).block();
        assertThat(settings.maxContentCharacters("get_post").block()).isEqualTo(100_000);
        assertThat(settings.maxContentCharacters("get_page").block()).isEqualTo(16);
    }

    @Test
    void rejectsInvalidValuesAndUnknownFieldsBeforeSaving() {
        var withNull = new HashMap<String, Object>();
        withNull.put(FIELD, null);
        for (var values : List.of(
                Map.<String, Object>of(FIELD, 0),
                Map.<String, Object>of(FIELD, -1),
                Map.<String, Object>of(FIELD, 2_000_001),
                Map.<String, Object>of(FIELD, "100"),
                Map.<String, Object>of(FIELD, true),
                Map.<String, Object>of(FIELD, Double.NaN),
                Map.<String, Object>of(FIELD, Double.POSITIVE_INFINITY),
                Map.<String, Object>of(FIELD, new BigDecimal("1.5")),
                Map.<String, Object>of(FIELD, new BigInteger("9999999999999999999999999999")),
                Map.<String, Object>of("other", 100),
                withNull)) {
            StepVerifier.create(settings.configuration("get_post").getSave().apply(values))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(settings.configuration("get_post").getSave().apply(null))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(store, never()).saveConfiguration(anyString(), anyMap());
    }

    @Test
    void malformedStoredLimitsFailClosed() {
        for (var value : List.of("100", 0, 2_000_001, 1.5)) {
            stored.put("ai-foundation/get_post", Map.of(FIELD, value));
            StepVerifier.create(settings.maxContentCharacters("get_post"))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Test
    void storageErrorsPropagateAndOtherToolNamesAreNotAccepted() {
        var failure = new IllegalStateException("Unable to load settings");
        when(store.configuration("ai-foundation/get_post")).thenReturn(Mono.error(failure));
        StepVerifier.create(settings.maxContentCharacters("get_post"))
                .expectErrorMatches(error -> error == failure)
                .verify();
        assertThatThrownBy(() -> settings.configuration("list_posts")).isInstanceOf(IllegalArgumentException.class);
        StepVerifier.create(settings.maxContentCharacters("other_plugin/get_post"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
