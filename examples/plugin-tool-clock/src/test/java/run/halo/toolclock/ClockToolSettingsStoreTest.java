package run.halo.toolclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import tools.jackson.databind.json.JsonMapper;

class ClockToolSettingsStoreTest {
    private static final String CONFIG_MAP_NAME = "tool-clock-example-configmap";
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final PluginContext pluginContext = mock(PluginContext.class);
    private ClockToolSettingsStore store;

    @BeforeEach
    void setUp() {
        when(pluginContext.getConfigMapName()).thenReturn(CONFIG_MAP_NAME);
        store = new ClockToolSettingsStore(client, pluginContext);
    }

    @Test
    void missingConfigurationIsNotReadyAndHasDefaultFormat() {
        when(client.fetch(ConfigMap.class, CONFIG_MAP_NAME)).thenReturn(Mono.empty());
        StepVerifier.create(store.read())
                .assertNext(settings -> {
                    assertThat(settings.ready()).isFalse();
                    assertThat(settings.values()).containsEntry("zoneId", "").containsEntry("format", "datetime");
                })
                .verifyComplete();
    }

    @Test
    void savesOnlyClockGroupAndPreservesSelectedToolsAndOtherSettings() {
        var selector = "{\"toolIds\":[\"tool-clock-example/current_time\"]}";
        var configMap = configMap(Map.of("selector", selector, "anotherGroup", "{\"enabled\":true}"));
        when(client.fetch(ConfigMap.class, CONFIG_MAP_NAME)).thenReturn(Mono.just(configMap));
        when(client.update(any(ConfigMap.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(store.save(Map.of("zoneId", " Asia/Shanghai ", "format", "date")))
                .verifyComplete();

        var saved = ArgumentCaptor.forClass(ConfigMap.class);
        verify(client).update(saved.capture());
        assertThat(saved.getValue().getData())
                .containsEntry("selector", selector)
                .containsEntry("anotherGroup", "{\"enabled\":true}");
        var clockValues =
                JsonMapper.builder().build().readTree(saved.getValue().getData().get("clockTool"));
        assertThat(clockValues.path("zoneId").asString()).isEqualTo("Asia/Shanghai");
        assertThat(clockValues.path("format").asString()).isEqualTo("date");
        StepVerifier.create(store.read())
                .assertNext(settings -> assertThat(settings.ready()).isTrue())
                .verifyComplete();
    }

    @Test
    void createsItsOwnConfigMapWhenAbsent() {
        when(client.fetch(ConfigMap.class, CONFIG_MAP_NAME)).thenReturn(Mono.empty());
        when(client.create(any(ConfigMap.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        StepVerifier.create(store.save(Map.of("zoneId", "UTC"))).verifyComplete();
        var created = ArgumentCaptor.forClass(ConfigMap.class);
        verify(client).create(created.capture());
        assertThat(created.getValue().getMetadata().getName()).isEqualTo(CONFIG_MAP_NAME);
        assertThat(created.getValue().getData()).containsOnlyKeys("clockTool");
        var values = JsonMapper.builder()
                .build()
                .readTree(created.getValue().getData().get("clockTool"));
        assertThat(values.path("format").asString()).isEqualTo("datetime");
    }

    @Test
    void refetchesOnConflictSoConcurrentSelectorChangesArePreserved() {
        var before = configMap(Map.of("selector", "{\"toolIds\":[]}"));
        var latestSelector = "{\"toolIds\":[\"tool-clock-example/current_time\"]}";
        var latest = configMap(Map.of("selector", latestSelector));
        when(client.fetch(ConfigMap.class, CONFIG_MAP_NAME)).thenReturn(Mono.just(before), Mono.just(latest));
        when(client.update(any(ConfigMap.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("Concurrent update")), Mono.just(latest));

        StepVerifier.create(store.save(Map.of("zoneId", "UTC", "format", "time")))
                .verifyComplete();

        var saved = ArgumentCaptor.forClass(ConfigMap.class);
        verify(client, times(2)).update(saved.capture());
        assertThat(saved.getAllValues().getLast().getData()).containsEntry("selector", latestSelector);
        verify(client, times(2)).fetch(ConfigMap.class, CONFIG_MAP_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Not/A-Timezone", "Asia/Invalid"})
    void rejectsInvalidTimezonesBeforeWriting(String zone) {
        StepVerifier.create(store.save(Map.of("zoneId", zone, "format", "datetime")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "custom", "DATE", "yyyy-MM-dd"})
    void rejectsUnsupportedFormatsBeforeWriting(String format) {
        StepVerifier.create(store.save(Map.of("zoneId", "UTC", "format", format)))
                .expectErrorMessage("输出格式必须为 datetime、date 或 time")
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void rejectsWrongValueTypesBeforeWriting() {
        StepVerifier.create(store.save(Map.of("zoneId", 8, "format", "date")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(store.save(Map.of("zoneId", "UTC", "format", 8)))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(store.save(null))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    private static ConfigMap configMap(Map<String, String> data) {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(CONFIG_MAP_NAME);
        configMap.setMetadata(metadata);
        configMap.setData(data);
        return configMap;
    }
}
