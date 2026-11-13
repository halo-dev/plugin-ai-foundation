package run.halo.aifoundation.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ToolSettingsStoreTest {
    private static final String CONFIG_MAP = "ai-foundation-configmap";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    ReactiveExtensionClient client;

    @Mock
    PluginContext pluginContext;

    ToolSettingsStore store;

    @BeforeEach
    void setUp() {
        when(pluginContext.getConfigMapName()).thenReturn(CONFIG_MAP);
        store = new ToolSettingsStore(client, pluginContext);
    }

    @Test
    void absentChoicesLeaveNewToolsEnabledAndUnconfigured() {
        when(client.fetch(ConfigMap.class, CONFIG_MAP)).thenReturn(Mono.empty());
        assertThat(store.enabledStates().block()).isEmpty();
        assertThat(store.configuration("ai-foundation/web_search").block()).isEmpty();
    }

    @Test
    void saveStatePreservesToolConfigurationAndDefaultModels() {
        var map = configMap("""
            {"ai-foundation/web_search":{"values":{"braveApiKeySecretName":"brave-key"}}}
            """);
        map.putDataItem("defaultModels", "existing default slots");
        when(client.fetch(ConfigMap.class, CONFIG_MAP)).thenReturn(Mono.just(map));
        when(client.update(any(ConfigMap.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        store.setEnabled("ai-foundation/web_search", false).block();

        assertThat(map.getData().get("defaultModels")).isEqualTo("existing default slots");
        var entry = JSON.readTree(map.getData().get("sharedTools")).get("ai-foundation/web_search");
        assertThat(entry.get("enabled").booleanValue()).isFalse();
        assertThat(entry.get("values").get("braveApiKeySecretName").stringValue())
                .isEqualTo("brave-key");
        assertThat(store.enabledStates().block()).containsEntry("ai-foundation/web_search", false);
    }

    @Test
    void configurationChangeDoesNotReenableDisabledTool() {
        var map = configMap("{\"ai-foundation/web_search\":{\"enabled\":false}}");
        when(client.fetch(ConfigMap.class, CONFIG_MAP)).thenReturn(Mono.just(map));
        when(client.update(any(ConfigMap.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        store.saveConfiguration("ai-foundation/web_search", Map.of("braveApiKeySecretName", "key-ref"))
                .block();

        assertThat(store.enabledStates().block()).containsEntry("ai-foundation/web_search", false);
        assertThat(store.configuration("ai-foundation/web_search").block())
                .containsEntry("braveApiKeySecretName", "key-ref");
    }

    @Test
    void conflictRetryMergesLatestChoicesFromAnotherTool() {
        var first = configMap("{}");
        var latest = configMap("{\"other-plugin/clock\":{\"enabled\":false}}");
        when(client.fetch(ConfigMap.class, CONFIG_MAP)).thenReturn(Mono.just(first), Mono.just(latest));
        when(client.update(any(ConfigMap.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("changed")))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));

        store.setEnabled("ai-foundation/read_web_page", false).block();

        var entries = JSON.readTree(latest.getData().get("sharedTools"));
        assertThat(entries.get("other-plugin/clock").get("enabled").booleanValue())
                .isFalse();
        assertThat(entries.get("ai-foundation/read_web_page").get("enabled").booleanValue())
                .isFalse();
    }

    @Test
    void createsSharedConfigMapWhenAbsent() {
        when(client.fetch(ConfigMap.class, CONFIG_MAP)).thenReturn(Mono.empty());
        when(client.create(any(ConfigMap.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        store.setEnabled("ai-foundation/search_site", false).block();

        var captured = ArgumentCaptor.forClass(ConfigMap.class);
        verify(client).create(captured.capture());
        assertThat(captured.getValue().getMetadata().getName()).isEqualTo(CONFIG_MAP);
        assertThat(JSON.readTree(captured.getValue().getData().get("sharedTools"))
                        .get("ai-foundation/search_site")
                        .get("enabled")
                        .booleanValue())
                .isFalse();
    }

    @Test
    void unreadableOrInvalidSettingsNeverFallBackToEnabled() {
        when(client.fetch(ConfigMap.class, CONFIG_MAP)).thenReturn(Mono.error(new IllegalStateException("unreadable")));
        StepVerifier.create(store.enabledStates())
                .expectErrorMessage("unreadable")
                .verify();

        when(client.fetch(ConfigMap.class, CONFIG_MAP))
                .thenReturn(Mono.just(configMap("{\"plugin/tool\":{\"enabled\":\"false\"}}")));
        StepVerifier.create(store.enabledStates())
                .expectErrorMessage("Shared tool settings are invalid")
                .verify();
    }

    private static ConfigMap configMap(String sharedTools) {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(CONFIG_MAP);
        configMap.setMetadata(metadata);
        configMap.putDataItem("sharedTools", sharedTools);
        return configMap;
    }
}
