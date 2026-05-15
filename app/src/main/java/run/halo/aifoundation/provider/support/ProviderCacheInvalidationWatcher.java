package run.halo.aifoundation.provider.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Extension;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.extension.Watcher;
import run.halo.aifoundation.extension.AiProvider;

@Slf4j
@Component
public class ProviderCacheInvalidationWatcher implements Watcher {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;

    private volatile boolean disposed = false;
    private Runnable disposeHook;

    private static final GroupVersionKind AI_PROVIDER_GVK =
        GroupVersionKind.fromExtension(AiProvider.class);
    private static final GroupVersionKind SECRET_GVK =
        GroupVersionKind.fromExtension(Secret.class);

    public ProviderCacheInvalidationWatcher(ReactiveExtensionClient client,
        ProviderClientCache providerClientCache) {
        this.client = client;
        this.providerClientCache = providerClientCache;
    }

    @PostConstruct
    public void register() {
        client.watch(this);
        log.debug("Registered ProviderCacheInvalidationWatcher");
    }

    @PreDestroy
    public void unregister() {
        dispose();
        log.debug("Unregistered ProviderCacheInvalidationWatcher");
    }

    @Override
    public void onUpdate(Extension oldExtension, Extension newExtension) {
        if (isDisposed() || newExtension == null) {
            return;
        }
        var gvk = newExtension.groupVersionKind();
        if (gvk == null) {
            return;
        }
        if (AI_PROVIDER_GVK.equals(gvk)) {
            var providerName = newExtension.getMetadata().getName();
            providerClientCache.invalidate(providerName);
            log.info("Invalidated cache for AiProvider update: {}", providerName);
        } else if (SECRET_GVK.equals(gvk)) {
            var secretName = newExtension.getMetadata().getName();
            invalidateProvidersBySecret(secretName);
        }
    }

    @Override
    public void onDelete(Extension extension) {
        if (isDisposed() || extension == null) {
            return;
        }
        var gvk = extension.groupVersionKind();
        if (gvk == null) {
            return;
        }
        if (AI_PROVIDER_GVK.equals(gvk)) {
            var providerName = extension.getMetadata().getName();
            providerClientCache.invalidate(providerName);
            log.info("Invalidated cache for AiProvider delete: {}", providerName);
        }
    }

    private void invalidateProvidersBySecret(String secretName) {
        client.listAll(AiProvider.class, new ListOptions(), Sort.unsorted())
            .filter(provider -> secretName.equals(provider.getSpec().getApiKeySecretName()))
            .map(provider -> provider.getMetadata().getName())
            .collectList()
            .subscribe(providerNames -> {
                providerNames.forEach(providerClientCache::invalidate);
                if (!providerNames.isEmpty()) {
                    log.info("Invalidated cache for {} providers referencing secret: {}",
                        providerNames.size(), secretName);
                }
            }, error -> log.error("Failed to invalidate providers by secret: {}", secretName, error));
    }

    @Override
    public void registerDisposeHook(Runnable dispose) {
        this.disposeHook = dispose;
    }

    @Override
    public void dispose() {
        if (isDisposed()) {
            return;
        }
        this.disposed = true;
        if (this.disposeHook != null) {
            this.disposeHook.run();
        }
    }

    @Override
    public boolean isDisposed() {
        return this.disposed;
    }
}
