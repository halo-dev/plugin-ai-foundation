package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.service.tool.ToolSettingsStore;

class WebPageReaderSettingsTest {
    private static final String TOOL_ID = WebPageReaderSettings.TOOL_ID;
    private static final String VPN_FIELD = "vpnCompatibilityEnabled";
    private static final String SIZE_FIELD = "maxResponseSizeMb";
    private static final String CONTENT_FIELD = "maxContentCharacters";
    private static final Map<String, Object> DEFAULT_VALUES =
            Map.of(VPN_FIELD, false, SIZE_FIELD, 10, CONTENT_FIELD, 100_000);
    private final ToolSettingsStore store = mock(ToolSettingsStore.class);
    private final AtomicReference<Map<String, Object>> stored = new AtomicReference<>(Map.of());
    private WebPageReaderSettings settings;

    @BeforeEach
    void setUp() {
        settings = new WebPageReaderSettings(store);
        when(store.configuration(TOOL_ID)).thenAnswer(ignored -> Mono.just(stored.get()));
        when(store.saveConfiguration(eq(TOOL_ID), anyMap()))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> stored.set(invocation.getArgument(1))));
    }

    @Test
    void exposesTheNativeBooleanSwitchAndIntegerInputsWithLimitsAndUnits() {
        var schema = settings.configuration().getSchema();
        assertThat(schema).hasSize(3);
        assertThat(schema.getFirst())
                .containsEntry("$formkit", "switch")
                .containsEntry("name", VPN_FIELD)
                .containsEntry("label", "VPN 兼容模式")
                .containsEntry("value", false)
                .containsEntry("onValue", true)
                .containsEntry("offValue", false);
        assertThat(schema.get(1))
                .containsEntry("$formkit", "number")
                .containsEntry("name", SIZE_FIELD)
                .containsEntry("label", "下载大小上限（MiB）")
                .containsEntry("number", "integer")
                .containsEntry("value", 10)
                .containsEntry("min", 1)
                .containsEntry("max", 100)
                .containsEntry("step", 1)
                .containsEntry("validation", "required|number|between:1,100");
        assertThat(schema.get(2))
                .containsEntry("$formkit", "number")
                .containsEntry("name", CONTENT_FIELD)
                .containsEntry("label", "正文长度上限（字符）")
                .containsEntry("number", "integer")
                .containsEntry("value", 100_000)
                .containsEntry("min", 1)
                .containsEntry("max", 2_000_000)
                .containsEntry("step", 1)
                .containsEntry("validation", "required|number|between:1,2000000");
    }

    @Test
    void defaultsToVpnOffTenMebibytesAndOneHundredThousandCharacters() {
        var options = settings.readOptions().block();
        assertThat(options).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 10, 100_000));
        assertThat(options.maxResponseBytes()).isEqualTo(10 * 1_048_576);
        assertThat(settings.vpnCompatibilityEnabled().block()).isFalse();
        assertThat(settings.configuration().getValues().get().block()).containsExactlyEntriesOf(DEFAULT_VALUES);
    }

    @Test
    void emptyConfigurationPublisherAlsoUsesDefaults() {
        when(store.configuration(TOOL_ID)).thenReturn(Mono.empty());
        assertThat(settings.configuration().getValues().get().block()).containsExactlyEntriesOf(DEFAULT_VALUES);
    }

    @Test
    void usesDefaultLimitsWhenOnlyTheVpnSwitchIsConfigured() {
        stored.set(Map.of(VPN_FIELD, true));
        assertThat(settings.readOptions().block()).isEqualTo(new WebPageReaderSettings.ReadOptions(true, 10, 100_000));
        assertThat(settings.configuration().getValues().get().block())
                .containsExactlyEntriesOf(Map.of(VPN_FIELD, true, SIZE_FIELD, 10, CONTENT_FIELD, 100_000));
        stored.set(Map.of("obsoleteSetting", true));
        assertThat(settings.configuration().getValues().get().block()).containsExactlyEntriesOf(DEFAULT_VALUES);
    }

    @Test
    void fillsEachMissingFieldIndependently() {
        stored.set(Map.of(CONTENT_FIELD, 25_000));
        assertThat(settings.readOptions().block()).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 10, 25_000));
        settings.configuration().getSave().apply(Map.of(SIZE_FIELD, 20)).block();
        assertThat(stored.get())
                .containsExactlyEntriesOf(Map.of(VPN_FIELD, false, SIZE_FIELD, 20, CONTENT_FIELD, 100_000));
    }

    @Test
    void savesCanonicalValuesWithoutChangingTheToolEnabledState() {
        settings.configuration()
                .getSave()
                .apply(Map.of(VPN_FIELD, true, SIZE_FIELD, 20L, CONTENT_FIELD, new BigDecimal("250000.000")))
                .block();
        assertThat(stored.get())
                .containsExactlyEntriesOf(Map.of(VPN_FIELD, true, SIZE_FIELD, 20, CONTENT_FIELD, 250_000));
        assertThat(stored.get().get(SIZE_FIELD)).isInstanceOf(Integer.class);
        assertThat(stored.get().get(CONTENT_FIELD)).isInstanceOf(Integer.class);
        assertThat(settings.readOptions().block()).isEqualTo(new WebPageReaderSettings.ReadOptions(true, 20, 250_000));
        verify(store, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void emptySaveResetsAllValuesToDefaultsWithoutEnablingTheTool() {
        stored.set(Map.of(VPN_FIELD, true, SIZE_FIELD, 100, CONTENT_FIELD, 2_000_000));
        settings.configuration().getSave().apply(Map.of()).block();
        assertThat(stored.get()).containsExactlyEntriesOf(DEFAULT_VALUES);
        assertThat(settings.readOptions().block()).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 10, 100_000));
        verify(store, never()).setEnabled(anyString(), anyBoolean());
    }

    @Test
    void acceptsBothInclusiveBoundaries() {
        settings.configuration()
                .getSave()
                .apply(Map.of(SIZE_FIELD, 1, CONTENT_FIELD, 1))
                .block();
        assertThat(settings.readOptions().block()).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 1, 1));
        settings.configuration()
                .getSave()
                .apply(Map.of(SIZE_FIELD, 100, CONTENT_FIELD, 2_000_000))
                .block();
        var upper = settings.readOptions().block();
        assertThat(upper).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 100, 2_000_000));
        assertThat(upper.maxResponseBytes()).isEqualTo(104_857_600);
    }

    @ParameterizedTest
    @MethodSource("integralNumbers")
    void acceptsStandardIntegralNumberRepresentations(Number number) {
        settings.configuration()
                .getSave()
                .apply(Map.of(SIZE_FIELD, number, CONTENT_FIELD, number))
                .block();
        assertThat(stored.get()).containsExactlyEntriesOf(Map.of(VPN_FIELD, false, SIZE_FIELD, 10, CONTENT_FIELD, 10));
    }

    static Stream<Number> integralNumbers() {
        return Stream.<Number>of((byte) 10, (short) 10, 10, 10L, 10.0, 10.0f, BigInteger.TEN, new BigDecimal("10.000"));
    }

    @ParameterizedTest
    @MethodSource("invalidLimits")
    void rejectsInvalidNumbersOnSaveAndReadWithoutPersisting(String field, Object invalidValue) {
        var values = new HashMap<String, Object>();
        values.put(field, invalidValue);
        StepVerifier.create(settings.configuration().getSave().apply(values))
                .expectError(IllegalArgumentException.class)
                .verify();
        stored.set(values);
        StepVerifier.create(settings.readOptions())
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(settings.configuration().getValues().get())
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(store, never()).saveConfiguration(anyString(), anyMap());
        verify(store, never()).setEnabled(anyString(), anyBoolean());
    }

    static Stream<Arguments> invalidLimits() {
        var invalid = Arrays.asList(
                null,
                "10",
                "",
                true,
                List.of(10),
                0,
                -1,
                1.5,
                new BigDecimal("10.01"),
                new BigDecimal("1E100"),
                Long.MAX_VALUE,
                new BigInteger("99999999999999999999999999999999"),
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Float.NaN,
                Float.POSITIVE_INFINITY);
        return Stream.concat(
                Stream.of(SIZE_FIELD, CONTENT_FIELD)
                        .flatMap(field -> invalid.stream().map(value -> Arguments.of(field, value))),
                Stream.of(Arguments.of(SIZE_FIELD, 101), Arguments.of(CONTENT_FIELD, 2_000_001)));
    }

    @Test
    void rejectsNullUnknownKeysAndMalformedBooleansBeforeSaving() {
        var withNull = new HashMap<String, Object>();
        withNull.put(VPN_FIELD, null);
        var withNullKey = new HashMap<String, Object>();
        withNullKey.put(null, true);
        for (var values : List.of(
                Map.<String, Object>of("enabled", true),
                Map.<String, Object>of(VPN_FIELD, 1),
                Map.<String, Object>of(VPN_FIELD, "true"),
                Map.<String, Object>of(VPN_FIELD, ""),
                withNull,
                withNullKey)) {
            StepVerifier.create(settings.configuration().getSave().apply(values))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(settings.configuration().getSave().apply(null))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(store, never()).saveConfiguration(anyString(), anyMap());
    }

    @Test
    void malformedPersistedBooleanFailsClosed() {
        for (var invalid : List.of(Map.<String, Object>of(VPN_FIELD, "true"), Map.<String, Object>of(VPN_FIELD, 1))) {
            stored.set(invalid);
            StepVerifier.create(settings.readOptions())
                    .expectError(IllegalArgumentException.class)
                    .verify();
            StepVerifier.create(settings.vpnCompatibilityEnabled())
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    @Test
    void readsOneCurrentConfigurationForEverySubscription() {
        var options = settings.readOptions();
        assertThat(options.block()).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 10, 100_000));
        stored.set(Map.of(VPN_FIELD, true, SIZE_FIELD, 50, CONTENT_FIELD, 1_000_000));
        assertThat(options.block()).isEqualTo(new WebPageReaderSettings.ReadOptions(true, 50, 1_000_000));
        stored.set(Map.of(VPN_FIELD, false, SIZE_FIELD, 1, CONTENT_FIELD, 12_000));
        assertThat(options.block()).isEqualTo(new WebPageReaderSettings.ReadOptions(false, 1, 12_000));
        verify(store, times(3)).configuration(TOOL_ID);
    }

    @Test
    void storageErrorsPropagateInsteadOfUsingDefaultsOrEnablingCompatibility() {
        var failure = new IllegalStateException("Tool settings are unavailable");
        when(store.configuration(TOOL_ID)).thenReturn(Mono.error(failure));
        StepVerifier.create(settings.readOptions())
                .expectErrorMatches(error -> error == failure)
                .verify();
        StepVerifier.create(settings.vpnCompatibilityEnabled())
                .expectErrorMatches(error -> error == failure)
                .verify();
        StepVerifier.create(settings.configuration().getValues().get())
                .expectErrorMatches(error -> error == failure)
                .verify();
    }
}
