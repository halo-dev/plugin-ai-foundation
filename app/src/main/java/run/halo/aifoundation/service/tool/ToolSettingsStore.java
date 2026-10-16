package run.halo.aifoundation.service.tool;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists administrator enablement and built-in settings in the plugin ConfigMap.
 *
 * <p>Entries are keyed by stable catalog IDs, so disabling a provider does not discard its saved choices. Configuration
 * stores ordinary values and Secret references only. The store snapshots submitted JSON values and retries conflicting
 * writes while retaining unrelated ConfigMap data.
 *
 * <p>For example, {@code setEnabled("ai-foundation/read_web_page", false)} records the global availability choice;
 * provider registration and tool resolution apply that saved state later.
 */
@Component
@RequiredArgsConstructor
public class ToolSettingsStore {
    private static final String GROUP = "sharedTools";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Map<String, Object>>> ENTRIES = new TypeReference<>() {};

    private final ReactiveExtensionClient client;
    private final PluginContext pluginContext;

    /**
     * Reads saved global enablement choices; entries without a choice default to enabled.
     *
     * @return immutable catalog-ID-to-enablement map
     */
    public Mono<Map<String, Boolean>> enabledStates() {
        return read().map(entries -> {
            var states = new LinkedHashMap<String, Boolean>();
            entries.forEach(
                    (id, entry) -> states.put(id, entry.get("enabled") instanceof Boolean enabled ? enabled : true));
            return Collections.unmodifiableMap(states);
        });
    }

    /**
     * Changes one tool's global availability without replacing its saved configuration.
     *
     * @param id stable catalog ID
     * @param enabled whether administrators allow the tool to be used
     * @return completion after persistence
     */
    public Mono<Void> setEnabled(String id, boolean enabled) {
        return change(id, entry -> entry.put("enabled", enabled));
    }

    /**
     * Reads editable settings previously saved for a tool.
     *
     * @param id stable catalog ID
     * @return immutable values, or an empty map when no settings exist
     */
    public Mono<Map<String, Object>> configuration(String id) {
        return read().map(entries -> {
            var entry = entries.get(id);
            if (entry == null || entry.get("values") == null) {
                return Map.of();
            }
            @SuppressWarnings("unchecked")
            var values = (Map<String, Object>) entry.get("values");
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        });
    }

    /**
     * Saves a JSON snapshot of provider settings without replacing the enablement choice.
     *
     * <p>Values contain ordinary configuration or Secret references, never resolved credentials. Tool-specific
     * validation belongs to the provider before this storage boundary.
     *
     * @param id stable catalog ID
     * @param values validated editable configuration
     * @return completion after persistence
     */
    public Mono<Void> saveConfiguration(String id, Map<String, Object> values) {
        return Mono.defer(() -> {
            if (values == null) {
                return Mono.error(new IllegalArgumentException("Tool configuration is required"));
            }
            var snapshot = JSON.readValue(JSON.writeValueAsString(values), new TypeReference<Map<String, Object>>() {});
            return change(id, entry -> entry.put("values", snapshot));
        });
    }

    private Mono<Map<String, Map<String, Object>>> read() {
        return Mono.defer(() -> client.fetch(ConfigMap.class, pluginContext.getConfigMapName()))
                .map(this::readEntries)
                .defaultIfEmpty(Map.of());
    }

    private Map<String, Map<String, Object>> readEntries(ConfigMap configMap) {
        var payload = configMap.getData() == null ? null : configMap.getData().get(GROUP);
        if (payload == null) {
            return new LinkedHashMap<>();
        }
        var entries = JSON.readValue(payload, ENTRIES);
        if (entries == null) {
            throw new IllegalStateException("Shared tool settings are invalid");
        }
        entries.forEach(ToolSettingsStore::validateEntry);
        return entries;
    }

    private static void validateEntry(String id, Map<String, Object> entry) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Shared tool settings are invalid");
        }
        if (entry == null) {
            throw new IllegalStateException("Shared tool settings are invalid");
        }
        if (entry.containsKey("enabled") && !(entry.get("enabled") instanceof Boolean)) {
            throw new IllegalStateException("Shared tool settings are invalid");
        }
        if (entry.containsKey("values") && !(entry.get("values") instanceof Map)) {
            throw new IllegalStateException("Shared tool settings are invalid");
        }
    }

    private Mono<Void> change(String id, Consumer<Map<String, Object>> mutation) {
        return Mono.defer(() -> {
                    if (id == null || id.isBlank()) {
                        return Mono.error(new IllegalArgumentException("Tool ID is required"));
                    }
                    return client.fetch(ConfigMap.class, pluginContext.getConfigMapName())
                            .flatMap(configMap -> {
                                var entries = readEntries(configMap);
                                mutation.accept(entries.computeIfAbsent(id, ignored -> new LinkedHashMap<>()));
                                var data = configMap.getData() == null
                                        ? new LinkedHashMap<String, String>()
                                        : new LinkedHashMap<>(configMap.getData());
                                data.put(GROUP, JSON.writeValueAsString(entries));
                                configMap.setData(data);
                                return client.update(configMap);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                var entry = new LinkedHashMap<String, Object>();
                                mutation.accept(entry);
                                var configMap = new ConfigMap();
                                var metadata = new Metadata();
                                metadata.setName(pluginContext.getConfigMapName());
                                configMap.setMetadata(metadata);
                                configMap.putDataItem(GROUP, JSON.writeValueAsString(Map.of(id, entry)));
                                return client.create(configMap);
                            }));
                })
                .retryWhen(Retry.backoff(3, Duration.ofMillis(20))
                        .filter(ToolSettingsStore::isConflict)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .then();
    }

    private static boolean isConflict(Throwable error) {
        return error instanceof OptimisticLockingFailureException
                || error instanceof DataIntegrityViolationException
                // Halo's duplicate-name exception is internal and absent from its public API JAR.
                || error.getClass().getName().equals("run.halo.app.infra.exception.DuplicateNameException");
    }
}
