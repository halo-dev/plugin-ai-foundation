package run.halo.aifoundation.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.DefaultModelNotConfiguredException;
import run.halo.aifoundation.EmbeddingModel;
import run.halo.aifoundation.IncompatibleModelTypeException;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.ModelDisabledException;
import run.halo.aifoundation.ModelInfo;
import run.halo.aifoundation.ModelNotFoundException;
import run.halo.aifoundation.ProviderDisabledException;
import run.halo.aifoundation.ProviderInfo;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.setting.DefaultModelSlots;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;
    private final ReactiveSettingFetcher settingFetcher;

    @Override
    public Mono<LanguageModel> languageModel(String modelName) {
        return fetchAiModel(modelName)
            .flatMap(aiModel -> {
                if (!aiModel.getSpec().isEnabled()) {
                    return Mono.error(new ModelDisabledException(modelName));
                }
                var typeError = validateModelType(aiModel, ModelType.LANGUAGE, modelName);
                if (typeError != null) {
                    return Mono.error(typeError);
                }
                var providerName = aiModel.getSpec().getProviderName();
                var modelId = aiModel.getSpec().getModelId();
                return fetchProvider(providerName)
                    .flatMap(provider -> {
                        if (!provider.getSpec().isEnabled()) {
                            return Mono.error(new ProviderDisabledException(providerName));
                        }
                        return resolveApiKey(provider)
                            .map(apiKey -> {
                                var chatModel = providerClientCache
                                    .getOrCreateChatModel(provider, apiKey, modelId);
                                return new LanguageModelImpl(
                                    chatModel, provider.getSpec().getProviderType());
                            });
                    });
            });
    }

    @Override
    public Mono<EmbeddingModel> embeddingModel(String modelName) {
        return fetchAiModel(modelName)
            .flatMap(aiModel -> {
                if (!aiModel.getSpec().isEnabled()) {
                    return Mono.error(new ModelDisabledException(modelName));
                }
                var typeError = validateModelType(aiModel, ModelType.EMBEDDING, modelName);
                if (typeError != null) {
                    return Mono.error(typeError);
                }
                var providerName = aiModel.getSpec().getProviderName();
                var modelId = aiModel.getSpec().getModelId();
                return fetchProvider(providerName)
                    .flatMap(provider -> {
                        if (!provider.getSpec().isEnabled()) {
                            return Mono.error(new ProviderDisabledException(providerName));
                        }
                        return resolveApiKey(provider)
                            .map(apiKey -> {
                                AiProviderType type = providerClientCache
                                    .getProviderType(provider.getSpec().getProviderType());
                                var springEmbeddingModel = providerClientCache
                                    .getOrCreateEmbeddingModel(provider, apiKey, modelId);
                                if (springEmbeddingModel == null) {
                                    throw new ModelNotFoundException(
                                        "Provider '" + providerName
                                            + "' does not support embeddings");
                                }
                                return new EmbeddingModelImpl(
                                    springEmbeddingModel,
                                    provider.getSpec().getProviderType(),
                                    type.maxEmbeddingsPerCall(),
                                    type.supportsParallelCalls()
                                );
                            });
                    });
            });
    }

    @Override
    public Mono<LanguageModel> defaultLanguageModel() {
        return defaultSlotName("language", DefaultModelSlots::getLanguageModelName)
            .flatMap(this::languageModel);
    }

    @Override
    public Mono<EmbeddingModel> defaultEmbeddingModel() {
        return defaultSlotName("embedding", DefaultModelSlots::getEmbeddingModelName)
            .flatMap(this::embeddingModel);
    }

    @Override
    public Mono<List<ModelInfo>> listModels() {
        return client.listAll(AiModel.class, new ListOptions(),
            Sort.by("metadata.creationTimestamp").descending())
            .map(model -> ModelInfo.builder()
                .name(model.getMetadata().getName())
                .providerName(model.getSpec().getProviderName())
                .modelId(model.getSpec().getModelId())
                .displayName(model.getSpec().getDisplayName())
                .enabled(model.getSpec().isEnabled())
                .build())
            .collectList();
    }

    @Override
    public Mono<List<ProviderInfo>> listProviders() {
        return client.listAll(AiProvider.class, new ListOptions(),
            Sort.by("metadata.creationTimestamp").descending())
            .map(provider -> {
                var status = provider.getStatus();
                var phase = status != null && status.getPhase() != null
                    ? status.getPhase().name() : "UNKNOWN";
                var lastCheckedAt = status != null && status.getLastCheckedAt() != null
                    ? status.getLastCheckedAt().toString() : null;
                return ProviderInfo.builder()
                    .name(provider.getMetadata().getName())
                    .displayName(provider.getSpec().getDisplayName())
                    .providerType(provider.getSpec().getProviderType())
                    .enabled(provider.getSpec().isEnabled())
                    .phase(phase)
                    .lastCheckedAt(lastCheckedAt)
                    .build();
            })
            .collectList();
    }

    private Mono<AiModel> fetchAiModel(String modelName) {
        return client.fetch(AiModel.class, modelName)
            .switchIfEmpty(Mono.error(new ModelNotFoundException(modelName)));
    }

    private Mono<AiProvider> fetchProvider(String providerName) {
        return client.fetch(AiProvider.class, providerName)
            .switchIfEmpty(Mono.error(new ModelNotFoundException(
                "Provider not found: " + providerName)));
    }

    private Throwable validateModelType(AiModel model, ModelType expectedType, String modelName) {
        var actualType = model.getSpec().getModelType();
        if (actualType == expectedType) {
            return null;
        }
        var actualValue = actualType != null ? actualType.getValue() : "null";
        return new IncompatibleModelTypeException(modelName, expectedType.getValue(), actualValue);
    }

    private Mono<String> defaultSlotName(String slotName,
        java.util.function.Function<DefaultModelSlots, String> extractor) {
        return settingFetcher.fetch(DefaultModelSlots.GROUP, DefaultModelSlots.class)
            .switchIfEmpty(Mono.error(new DefaultModelNotConfiguredException(slotName)))
            .map(extractor)
            .flatMap(modelName -> {
                if (modelName == null || modelName.isBlank()) {
                    return Mono.error(new DefaultModelNotConfiguredException(slotName));
                }
                return Mono.just(modelName);
            });
    }

    private Mono<String> resolveApiKey(AiProvider provider) {
        var secretName = provider.getSpec().getApiKeySecretName();
        return secretResolver.resolveApiKey(secretName);
    }
}
