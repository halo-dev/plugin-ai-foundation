package run.halo.aifoundation.setting;

import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class DefaultModelSlotStore {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ReactiveExtensionClient client;
    private final PluginContext pluginContext;

    public Mono<DefaultModelSlots> get() {
        return client.fetch(ConfigMap.class, configMapName())
            .flatMap(this::readSlots)
            .defaultIfEmpty(new DefaultModelSlots());
    }

    public Mono<DefaultModelSlots> save(DefaultModelSlots slots) {
        var payload = JSON_MAPPER.writeValueAsString(slots);
        return client.fetch(ConfigMap.class, configMapName())
            .flatMap(configMap -> updateConfigMap(configMap, payload))
            .switchIfEmpty(Mono.defer(() -> createConfigMap(payload)))
            .thenReturn(slots);
    }

    private Mono<DefaultModelSlots> readSlots(ConfigMap configMap) {
        var data = configMap.getData();
        if (data == null) {
            return Mono.empty();
        }
        var payload = data.get(DefaultModelSlots.GROUP);
        if (payload == null || payload.isBlank()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> JSON_MAPPER.readValue(payload, DefaultModelSlots.class));
    }

    private Mono<ConfigMap> updateConfigMap(ConfigMap configMap, String payload) {
        var data = configMap.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
        } else {
            data = new LinkedHashMap<>(data);
        }
        data.put(DefaultModelSlots.GROUP, payload);
        configMap.setData(data);
        return client.update(configMap);
    }

    private Mono<ConfigMap> createConfigMap(String payload) {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(configMapName());
        configMap.setMetadata(metadata);
        configMap.putDataItem(DefaultModelSlots.GROUP, payload);
        return client.create(configMap);
    }

    private String configMapName() {
        return pluginContext.getConfigMapName();
    }
}
