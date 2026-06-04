package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.ai.chat.model.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.aifoundation.exception.IncompatibleModelTypeException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.exception.ProviderDisabledException;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.embedding.DefaultEmbeddingModelFactory;
import run.halo.aifoundation.service.embedding.EmbeddingModelRuntimeFactory;
import run.halo.aifoundation.service.language.DefaultLanguageModelFactory;
import run.halo.aifoundation.service.language.LanguageModelRuntimeFactory;
import run.halo.aifoundation.service.language.LanguageModelRuntimeSupport;
import run.halo.aifoundation.service.model.DefaultAiModelResolver;
import run.halo.aifoundation.setting.DefaultModelSlotStore;
import run.halo.aifoundation.setting.DefaultModelSlots;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class AiModelServiceImplTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderClientCache providerClientCache;

    @Mock
    SecretResolver secretResolver;

    @Mock
    DefaultModelSlotStore defaultModelSlotStore;

    AiModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiModelServiceImpl(
            new DefaultAiModelResolver(client, providerClientCache, secretResolver,
                defaultModelSlotStore),
            new DefaultLanguageModelFactory(providerClientCache,
                new LanguageModelRuntimeFactory(new LanguageModelRuntimeSupport())),
            new DefaultEmbeddingModelFactory(providerClientCache, new EmbeddingModelRuntimeFactory())
        );
    }

    // ---- languageModel — fetch by metadata.name ----

    @Test
    void languageModel_modelNotFound_emitsModelNotFoundException() {
        when(client.fetch(AiModel.class, "nonexistent-model")).thenReturn(Mono.empty());

        StepVerifier.create(service.languageModel("nonexistent-model"))
            .expectError(ModelNotFoundException.class)
            .verify();
    }

    @Test
    void languageModel_disabledModel_emitsModelDisabledException() {
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", false);
        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));

        StepVerifier.create(service.languageModel("openai-prod-gpt-4-abc"))
            .expectErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(run.halo.aifoundation.exception.ModelDisabledException.class)
                .hasMessageContaining("openai-prod-gpt-4-abc"))
            .verify();
    }

    @Test
    void languageModel_disabledProvider_emitsProviderDisabledException() {
        var provider = aiProvider("openai-prod", "openai", false);
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", true);

        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));

        StepVerifier.create(service.languageModel("openai-prod-gpt-4-abc"))
            .expectError(ProviderDisabledException.class)
            .verify();
    }

    @Test
    void languageModel_providerNotFound_emitsModelNotFoundException() {
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", true);
        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.empty());

        StepVerifier.create(service.languageModel("openai-prod-gpt-4-abc"))
            .expectErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("Provider not found"))
            .verify();
    }

    @Test
    void languageModel_wrongModelType_emitsIncompatibleModelTypeException() {
        var model = aiModel("embedding", "openai-prod", "text-embedding-3-small",
            "Embedding", true, ModelType.EMBEDDING);
        when(client.fetch(AiModel.class, "embedding")).thenReturn(Mono.just(model));

        StepVerifier.create(service.languageModel("embedding"))
            .expectError(IncompatibleModelTypeException.class)
            .verify();
    }

    @Test
    void languageModel_withoutNameAndMissingSlot_emitsDefaultModelNotConfiguredException() {
        when(defaultModelSlotStore.get()).thenReturn(Mono.just(new DefaultModelSlots()));

        StepVerifier.create(service.languageModel())
            .expectError(DefaultModelNotConfiguredException.class)
            .verify();
    }

    @Test
    void languageModel_withoutName_resolvesConfiguredModel() {
        var slots = defaultSlots("openai-prod-gpt-4-abc", null);
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", true);
        var provider = aiProvider("openai-prod", "openai", true);
        var chatModel = mock(ChatModel.class);
        var providerType = languageProviderType();

        when(defaultModelSlotStore.get()).thenReturn(Mono.just(slots));
        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));
        when(secretResolver.resolveApiKey(null)).thenReturn(Mono.just("sk-test"));
        when(providerClientCache.getProviderType("openai")).thenReturn(providerType);
        when(providerClientCache.getOrCreateChatModel(provider, "sk-test", "gpt-4"))
            .thenReturn(chatModel);

        StepVerifier.create(service.languageModel())
            .assertNext(languageModel -> assertThat(languageModel).isNotNull())
            .verifyComplete();
    }

    @Test
    void languageModel_blankName_resolvesConfiguredModel() {
        var slots = defaultSlots("openai-prod-gpt-4-abc", null);
        var model = aiModel("openai-prod-gpt-4-abc", "openai-prod", "gpt-4", "GPT-4", true);
        var provider = aiProvider("openai-prod", "openai", true);
        var chatModel = mock(ChatModel.class);
        var providerType = languageProviderType();

        when(defaultModelSlotStore.get()).thenReturn(Mono.just(slots));
        when(client.fetch(AiModel.class, "openai-prod-gpt-4-abc")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));
        when(secretResolver.resolveApiKey(null)).thenReturn(Mono.just("sk-test"));
        when(providerClientCache.getProviderType("openai")).thenReturn(providerType);
        when(providerClientCache.getOrCreateChatModel(provider, "sk-test", "gpt-4"))
            .thenReturn(chatModel);

        StepVerifier.create(service.languageModel("  "))
            .assertNext(languageModel -> assertThat(languageModel).isNotNull())
            .verifyComplete();
    }

    @Test
    void embeddingModel_withoutName_resolvesConfiguredModel() {
        var slots = defaultSlots(null, "openai-prod-embedding");
        var model = aiModel("openai-prod-embedding", "openai-prod",
            "text-embedding-3-small", "Embedding", true, ModelType.EMBEDDING);
        var provider = aiProvider("openai-prod", "openai", true);
        var springEmbeddingModel = mock(org.springframework.ai.embedding.EmbeddingModel.class);
        var providerType = mock(AiProviderType.class);

        when(defaultModelSlotStore.get()).thenReturn(Mono.just(slots));
        when(client.fetch(AiModel.class, "openai-prod-embedding")).thenReturn(Mono.just(model));
        when(client.fetch(AiProvider.class, "openai-prod")).thenReturn(Mono.just(provider));
        when(secretResolver.resolveApiKey(null)).thenReturn(Mono.just("sk-test"));
        when(providerClientCache.getProviderType("openai")).thenReturn(providerType);
        when(providerClientCache.getOrCreateEmbeddingModel(provider, "sk-test",
            "text-embedding-3-small")).thenReturn(springEmbeddingModel);

        StepVerifier.create(service.embeddingModel())
            .assertNext(embeddingModel -> assertThat(embeddingModel).isNotNull())
            .verifyComplete();
    }

    @Test
    void embeddingModel_blankNameAndMissingSlot_emitsDefaultModelNotConfiguredException() {
        when(defaultModelSlotStore.get()).thenReturn(Mono.just(new DefaultModelSlots()));

        StepVerifier.create(service.embeddingModel(""))
            .expectError(DefaultModelNotConfiguredException.class)
            .verify();
    }

    // ---- helpers ----

    private AiModel aiModel(String name, String providerName, String modelId,
                            String displayName, boolean enabled) {
        return aiModel(name, providerName, modelId, displayName, enabled, ModelType.LANGUAGE);
    }

    private AiModel aiModel(String name, String providerName, String modelId,
                            String displayName, boolean enabled, ModelType modelType) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName(providerName);
        spec.setModelId(modelId);
        spec.setDisplayName(displayName);
        spec.setEnabled(enabled);
        spec.setModelType(modelType);
        model.setSpec(spec);
        return model;
    }

    private AiProvider aiProvider(String name, String providerType, boolean enabled) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType(providerType);
        spec.setDisplayName(name);
        spec.setEnabled(enabled);
        provider.setSpec(spec);
        return provider;
    }

    private DefaultModelSlots defaultSlots(String languageModelName, String embeddingModelName) {
        var slots = new DefaultModelSlots();
        slots.setLanguageModelName(languageModelName);
        slots.setEmbeddingModelName(embeddingModelName);
        return slots;
    }

    private AiProviderType languageProviderType() {
        var type = mock(AiProviderType.class);
        when(type.languageModelProviderOptions()).thenReturn(LanguageModelProviderOptions.defaults());
        return type;
    }
}
