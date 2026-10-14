package run.halo.toolclock;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

/** Owns this example plugin's configuration independently of AI Foundation. */
@Component
public class ClockToolSettingsStore {
    private static final String GROUP = "clockTool";
    private static final Set<String> FORMATS = Set.of("datetime", "date", "time");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> VALUES = new TypeReference<>() { };

    private final ReactiveExtensionClient client;
    private final PluginContext pluginContext;

    public ClockToolSettingsStore(ReactiveExtensionClient client, PluginContext pluginContext) {
        this.client = client;
        this.pluginContext = pluginContext;
    }

    public Mono<Settings> read() {
        return Mono.defer(() -> client.fetch(ConfigMap.class, pluginContext.getConfigMapName()))
            .map(configMap -> {
                var payload = configMap.getData() == null ? null : configMap.getData().get(GROUP);
                if (payload == null || payload.isBlank()) {
                    return Settings.empty();
                }
                var values = JSON.readValue(payload, VALUES);
                if (values == null) {
                    throw new IllegalArgumentException("时钟工具配置无效");
                }
                var zoneId = values.get("zoneId") instanceof String zone ? zone.strip() : "";
                var format = values.get("format") == null ? "datetime"
                    : values.get("format") instanceof String text ? text : "";
                return new Settings(zoneId, format);
            })
            .defaultIfEmpty(Settings.empty());
    }

    public Mono<Map<String, Object>> values() {
        return read().map(Settings::values);
    }

    public Mono<Void> save(Map<String, Object> values) {
        return Mono.defer(() -> {
            var settings = validate(values);
            var payload = JSON.writeValueAsString(settings.values());
            return client.fetch(ConfigMap.class, pluginContext.getConfigMapName())
                .flatMap(configMap -> {
                    var data = configMap.getData() == null ? new LinkedHashMap<String, String>()
                        : new LinkedHashMap<>(configMap.getData());
                    data.put(GROUP, payload);
                    configMap.setData(data);
                    return client.update(configMap);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    var configMap = new ConfigMap();
                    var metadata = new Metadata();
                    metadata.setName(pluginContext.getConfigMapName());
                    configMap.setMetadata(metadata);
                    configMap.putDataItem(GROUP, payload);
                    return client.create(configMap);
                }));
        })
            .retryWhen(Retry.backoff(3, Duration.ofMillis(20))
                .filter(ClockToolSettingsStore::isConflict)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
            .then();
    }

    private static Settings validate(Map<String, Object> values) {
        if (values == null || !(values.get("zoneId") instanceof String zone)
            || zone.isBlank() || zone.length() > 100) {
            throw new IllegalArgumentException("请输入有效时区，例如 Asia/Shanghai 或 UTC");
        }
        var format = values.get("format") == null ? "datetime" : values.get("format");
        if (!(format instanceof String text) || !FORMATS.contains(text)) {
            throw new IllegalArgumentException("输出格式必须为 datetime、date 或 time");
        }
        var settings = new Settings(zone.strip(), text);
        if (!settings.ready()) {
            throw new IllegalArgumentException("请输入有效时区，例如 Asia/Shanghai 或 UTC");
        }
        return settings;
    }

    private static boolean isConflict(Throwable error) {
        return error instanceof OptimisticLockingFailureException
            || error instanceof DataIntegrityViolationException
            || error.getClass().getName().equals("run.halo.app.infra.exception.DuplicateNameException");
    }

    public record Settings(String zoneId, String format) {
        static Settings empty() {
            return new Settings("", "datetime");
        }

        public boolean ready() {
            if (zoneId == null || zoneId.isBlank() || format == null || !FORMATS.contains(format)) {
                return false;
            }
            try {
                ZoneId.of(zoneId);
                return true;
            } catch (DateTimeException ignored) {
                return false;
            }
        }

        public Map<String, Object> values() {
            return Map.of("zoneId", zoneId, "format", format);
        }
    }
}
