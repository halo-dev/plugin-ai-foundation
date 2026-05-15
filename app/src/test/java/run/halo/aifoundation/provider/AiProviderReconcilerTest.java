package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.app.extension.ExtensionClient;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.controller.ControllerBuilder;
import run.halo.app.extension.controller.Reconciler;

class AiProviderReconcilerTest {

    private final ExtensionClient client = mock(ExtensionClient.class);
    private final AiProviderReconciler reconciler = new AiProviderReconciler(client);

    @Test
    void reconcile_deletedProvider_deletesAssociatedModels() {
        var provider = deletedProvider("openai-prod");
        provider.getMetadata().setFinalizers(Set.of(AiProviderReconciler.FINALIZER_NAME));

        var model = model("openai-prod-gpt-4", "openai-prod", "gpt-4");

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Optional.of(provider));
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of(model));

        var result = reconciler.reconcile(new Reconciler.Request("openai-prod"));

        assertThat(result.reEnqueue()).isFalse();
        verify(client).delete(model);
        verify(client).update(provider);
        assertThat(provider.getMetadata().getFinalizers()).isEmpty();
    }

    @Test
    void reconcile_deletedProvider_noModels_completesGracefully() {
        var provider = deletedProvider("openai-prod");
        provider.getMetadata().setFinalizers(Set.of(AiProviderReconciler.FINALIZER_NAME));

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Optional.of(provider));
        when(client.listAll(eq(AiModel.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(List.of());

        var result = reconciler.reconcile(new Reconciler.Request("openai-prod"));

        assertThat(result.reEnqueue()).isFalse();
        verify(client, never()).delete(any(AiModel.class));
        verify(client).update(provider);
        assertThat(provider.getMetadata().getFinalizers()).isEmpty();
    }

    @Test
    void reconcile_existingProvider_addsFinalizer() {
        var provider = provider("openai-prod");
        provider.getMetadata().setFinalizers(null);

        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Optional.of(provider));

        var result = reconciler.reconcile(new Reconciler.Request("openai-prod"));

        assertThat(result.reEnqueue()).isFalse();
        verify(client, never()).delete(any(AiModel.class));
        verify(client).update(provider);
        assertThat(provider.getMetadata().getFinalizers())
            .contains(AiProviderReconciler.FINALIZER_NAME);
    }

    @Test
    void reconcile_providerNotFound_returnsDoNotRetry() {
        when(client.fetch(AiProvider.class, "missing")).thenReturn(Optional.empty());

        var result = reconciler.reconcile(new Reconciler.Request("missing"));

        assertThat(result.reEnqueue()).isFalse();
        verify(client, never()).delete(any());
        verify(client, never()).update(any());
    }

    @Test
    void setupWith_buildsController() {
        var builder = new ControllerBuilder(reconciler, client);

        var controller = reconciler.setupWith(builder);

        assertThat(controller).isNotNull();
    }

    private AiProvider provider(String name) {
        var p = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        p.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setDisplayName(name);
        spec.setProviderType("openai");
        p.setSpec(spec);
        return p;
    }

    private AiProvider deletedProvider(String name) {
        var p = provider(name);
        p.getMetadata().setDeletionTimestamp(Instant.now());
        return p;
    }

    private AiModel model(String name, String providerName, String modelId) {
        var m = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        m.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        m.setSpec(spec);
        return m;
    }
}
