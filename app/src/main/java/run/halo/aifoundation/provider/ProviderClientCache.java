package run.halo.aifoundation.provider;

import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.extension.AiProvider;

@Slf4j
@Component
public class ProviderClientCache {

    private final ConcurrentHashMap<String, ProviderAdapterHolder> cache =
        new ConcurrentHashMap<>();

    public ProviderAdapterHolder getOrCreate(AiProvider provider, String apiKey) {
        var name = provider.getMetadata().getName();
        return cache.computeIfAbsent(name, k -> {
            log.debug("Creating provider adapter for: {}", name);
            var adapter = ProviderAdapterFactory.create(provider, apiKey);
            return new ProviderAdapterHolder(adapter);
        });
    }

    public void invalidate(String providerName) {
        var removed = cache.remove(providerName);
        if (removed != null) {
            log.debug("Invalidated cached adapter for provider: {}", providerName);
        }
    }

    public void invalidateAll() {
        cache.clear();
        log.debug("Invalidated all cached provider adapters");
    }
}
