package run.halo.aifoundation.provider.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.aifoundation.extension.AiProvider;

@ExtendWith(MockitoExtension.class)
class ProviderCacheInvalidationWatcherTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderClientCache providerClientCache;

    ProviderCacheInvalidationWatcher watcher;

    @BeforeEach
    void setUp() {
        watcher = new ProviderCacheInvalidationWatcher(client, providerClientCache);
    }

    @Test
    void onUpdate_aiProvider_triggersInvalidate() {
        var provider = aiProvider("openai-prod", "openai");

        watcher.onUpdate(null, provider);

        verify(providerClientCache).invalidate("openai-prod");
    }

    @Test
    void onUpdate_secret_noReferencingProviders_doesNothing() {
        var secret = new Secret();
        var metadata = new Metadata();
        metadata.setName("my-secret");
        secret.setMetadata(metadata);

        when(client.listAll(eq(AiProvider.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        watcher.onUpdate(null, secret);

        // Allow async subscription to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(providerClientCache, never()).invalidate(any());
    }

    @Test
    void onUpdate_whenDisposed_doesNothing() {
        var provider = aiProvider("openai-prod", "openai");
        watcher.dispose();

        watcher.onUpdate(null, provider);

        verify(providerClientCache, never()).invalidate(any());
    }

    @Test
    void onDelete_aiProvider_triggersInvalidate() {
        var provider = aiProvider("openai-prod", "openai");

        watcher.onDelete(provider);

        verify(providerClientCache).invalidate("openai-prod");
    }

    @Test
    void onDelete_nonAiProvider_doesNothing() {
        var extension = unrelatedExtension("unrelated");

        watcher.onDelete(extension);

        verify(providerClientCache, never()).invalidate(any());
    }

    @Test
    void onDelete_secret_triggersInvalidateForReferencingProviders() {
        var secret = new Secret();
        var metadata = new Metadata();
        metadata.setName("api-key-secret");
        secret.setMetadata(metadata);

        var provider1 = aiProvider("provider-a", "openai");
        provider1.getSpec().setApiKeySecretName("api-key-secret");
        var provider2 = aiProvider("provider-b", "deepseek");
        provider2.getSpec().setApiKeySecretName("api-key-secret");
        var provider3 = aiProvider("provider-c", "ollama");
        provider3.getSpec().setApiKeySecretName("other-secret");

        when(client.listAll(eq(AiProvider.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(provider1, provider2, provider3));

        watcher.onDelete(secret);

        // Allow async subscription to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(providerClientCache).invalidate("provider-a");
        verify(providerClientCache).invalidate("provider-b");
        verify(providerClientCache, never()).invalidate("provider-c");
    }

    @Test
    void onDelete_whenDisposed_doesNothing() {
        var provider = aiProvider("openai-prod", "openai");
        watcher.dispose();

        watcher.onDelete(provider);

        verify(providerClientCache, never()).invalidate(any());
    }

    @Test
    void onUpdate_secret_triggersInvalidateForReferencingProviders() {
        var secret = new Secret();
        var metadata = new Metadata();
        metadata.setName("api-key-secret");
        secret.setMetadata(metadata);

        var provider1 = aiProvider("provider-a", "openai");
        provider1.getSpec().setApiKeySecretName("api-key-secret");
        var provider2 = aiProvider("provider-b", "deepseek");
        provider2.getSpec().setApiKeySecretName("api-key-secret");
        var provider3 = aiProvider("provider-c", "ollama");
        provider3.getSpec().setApiKeySecretName("other-secret");

        when(client.listAll(eq(AiProvider.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(provider1, provider2, provider3));

        watcher.onUpdate(null, secret);

        // Allow async subscription to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(providerClientCache).invalidate("provider-a");
        verify(providerClientCache).invalidate("provider-b");
        verify(providerClientCache, never()).invalidate("provider-c");
    }

    @Test
    void dispose_setsDisposedState() {
        watcher.dispose();

        org.assertj.core.api.Assertions.assertThat(watcher.isDisposed()).isTrue();
    }

    private AiProvider aiProvider(String name, String providerType) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setDisplayName(name);
        provider.setSpec(spec);
        return provider;
    }

    private TestExtension unrelatedExtension(String name) {
        var extension = new TestExtension();
        extension.setApiVersion("test.halo.run/v1alpha1");
        extension.setKind("Unrelated");
        var metadata = new Metadata();
        metadata.setName(name);
        extension.setMetadata(metadata);
        return extension;
    }

    static class TestExtension extends AbstractExtension {
    }
}
