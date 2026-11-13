package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.service.tool.ToolSettingsStore;

class DocumentReaderSettingsTest {
    private final ToolSettingsStore store = mock(ToolSettingsStore.class);
    private final DocumentReaderSettings settings = new DocumentReaderSettings(store);
    private final Map<String, Map<String, Object>> saved = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(store.configuration(anyString()))
                .thenAnswer(invocation -> Mono.just(saved.getOrDefault(invocation.getArgument(0), Map.of())));
        when(store.saveConfiguration(anyString(), anyMap()))
                .thenAnswer(invocation ->
                        Mono.fromRunnable(() -> saved.put(invocation.getArgument(0), invocation.getArgument(1))));
    }

    @Test
    void usesIndependentGenerousDefaultsAndNativeFormInputs() {
        assertThat(settings.readOptions().block())
                .isEqualTo(new DocumentReaderSettings.Options(false, 20, 200_000, 200));
        assertThat(settings.importOptions().block().maxResponseBytes()).isEqualTo(20 * 1_048_576);
        var document = settings.configuration().getSchema();
        assertThat(document).hasSize(4);
        assertThat(document.getFirst()).containsEntry("$formkit", "number").containsEntry("value", 20);
        assertThat(document.get(1))
                .containsEntry("name", "maxContentCharacters")
                .containsEntry("value", 200_000);
        assertThat(document.get(2)).containsEntry("name", "maxPages").containsEntry("value", 200);
        assertThat(document.get(3)).containsEntry("$formkit", "switch").containsEntry("value", false);
        assertThat(settings.importConfiguration().getSchema()).hasSize(2);
    }

    @Test
    void savesAndReadsOnlyTheSelectedToolsConfiguration() {
        settings.configuration()
                .getSave()
                .apply(Map.of(
                        "maxResponseSizeMb",
                        30,
                        "maxContentCharacters",
                        300_000,
                        "maxPages",
                        400,
                        "vpnCompatibilityEnabled",
                        true))
                .block();
        assertThat(saved).containsOnlyKeys(DocumentReaderSettings.DOCUMENT_ID);
        assertThat(settings.readOptions().block())
                .isEqualTo(new DocumentReaderSettings.Options(true, 30, 300_000, 400));
        assertThat(settings.importOptions().block().maxResponseSizeMb()).isEqualTo(20);
        settings.importConfiguration()
                .getSave()
                .apply(Map.of("maxResponseSizeMb", 40))
                .block();
        assertThat(saved.get(DocumentReaderSettings.IMPORT_ID))
                .containsEntry("maxResponseSizeMb", 40)
                .containsEntry("vpnCompatibilityEnabled", false)
                .doesNotContainKeys("maxPages", "maxContentCharacters");
        assertThat(settings.readOptions().block().maxResponseSizeMb()).isEqualTo(30);
    }

    @ParameterizedTest
    @MethodSource("invalidOptions")
    void rejectsInvalidOrUnsupportedOptionsBeforeSaving(Map<String, Object> input) {
        StepVerifier.create(settings.configuration().getSave().apply(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(store);
    }

    static Stream<Arguments> invalidOptions() {
        return Stream.of(
                Arguments.of(Map.of("maxResponseSizeMb", 0)),
                Arguments.of(Map.of("maxResponseSizeMb", 101)),
                Arguments.of(Map.of("maxResponseSizeMb", new BigDecimal("1.2"))),
                Arguments.of(Map.of("maxContentCharacters", 2_000_001)),
                Arguments.of(Map.of("maxPages", 2_001)),
                Arguments.of(Map.of("vpnCompatibilityEnabled", "true")),
                Arguments.of(Map.of("other", 1)));
    }

    @Test
    void failsClosedWhenSavedConfigurationCannotBeLoadedOrIsInvalid() {
        when(store.configuration(DocumentReaderSettings.DOCUMENT_ID))
                .thenReturn(Mono.error(new IllegalStateException("configuration unavailable")));
        StepVerifier.create(settings.readOptions())
                .expectError(IllegalStateException.class)
                .verify();
        saved.put(DocumentReaderSettings.IMPORT_ID, Map.of("vpnCompatibilityEnabled", 1));
        StepVerifier.create(settings.importOptions())
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
