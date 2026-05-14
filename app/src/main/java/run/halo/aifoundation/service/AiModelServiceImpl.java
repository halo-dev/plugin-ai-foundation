package run.halo.aifoundation.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.AiServices;
import run.halo.aifoundation.EmbeddingModel;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.ModelDisabledException;
import run.halo.aifoundation.ModelInfo;
import run.halo.aifoundation.ModelNotFoundException;
import run.halo.aifoundation.ProviderDisabledException;
import run.halo.aifoundation.ProviderInfo;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;

    @PostConstruct
    void init() {
        AiServices.setModelService(this);
    }

    @PreDestroy
    void destroy() {
        AiServices.clear();
    }

    @Override
    public LanguageModel languageModel(String modelName) {
        var aiModel = fetchAiModel(modelName);
        var providerName = aiModel.getSpec().getProviderName();
        var modelId = aiModel.getSpec().getModelId();
        var provider = fetchProvider(providerName);

        if (!aiModel.getSpec().isEnabled()) {
            throw new ModelDisabledException(modelName);
        }

        if (!provider.getSpec().isEnabled()) {
            throw new ProviderDisabledException(providerName);
        }

        var apiKey = resolveApiKey(provider);
        var chatModel = providerClientCache.getOrCreateChatModel(provider, apiKey, modelId);

        return new LanguageModelImpl(chatModel, provider.getSpec().getProviderType());
    }

    @Override
    public EmbeddingModel embeddingModel(String modelName) {
        var aiModel = fetchAiModel(modelName);
        var providerName = aiModel.getSpec().getProviderName();
        var modelId = aiModel.getSpec().getModelId();
        var provider = fetchProvider(providerName);

        if (!aiModel.getSpec().isEnabled()) {
            throw new ModelDisabledException(modelName);
        }

        if (!provider.getSpec().isEnabled()) {
            throw new ProviderDisabledException(providerName);
        }

        var apiKey = resolveApiKey(provider);
        AiProviderType type = providerClientCache.getProviderType(provider.getSpec().getProviderType());
        var springEmbeddingModel = providerClientCache.getOrCreateEmbeddingModel(provider, apiKey, modelId);

        if (springEmbeddingModel == null) {
            throw new ModelNotFoundException(
                "Provider '" + providerName + "' does not support embeddings");
        }

        return new EmbeddingModelImpl(
            springEmbeddingModel,
            provider.getSpec().getProviderType(),
            type.maxEmbeddingsPerCall(),
            type.supportsParallelCalls()
        );
    }

    @Override
    public Mono<List<ModelInfo>> listModels() {
        return client.listAll(AiModel.class, new ListOptions(), Sort.unsorted())
            .map(model -> ModelInfo.builder()
                .name(model.getMetadata().getName())
                .providerName(model.getSpec().getProviderName())
                .modelId(model.getSpec().getModelId())
                .displayName(model.getSpec().getDisplayName())
                .build())
            .collectList();
    }

    @Override
    public Mono<List<ProviderInfo>> listProviders() {
        return client.listAll(AiProvider.class, new ListOptions(), Sort.unsorted())
            .map(provider -> {
                var status = provider.getStatus();
                var phase = status != null && status.getPhase() != null
                    ? status.getPhase().name() : "UNKNOWN";
                return ProviderInfo.builder()
                    .name(provider.getMetadata().getName())
                    .displayName(provider.getSpec().getDisplayName())
                    .providerType(provider.getSpec().getProviderType())
                    .enabled(provider.getSpec().isEnabled())
                    .phase(phase)
                    .build();
            })
            .collectList();
    }

    private AiModel fetchAiModel(String modelName) {
        return client.fetch(AiModel.class, modelName)
            .blockOptional()
            .orElseThrow(() -> new ModelNotFoundException(modelName));
    }

    private AiProvider fetchProvider(String providerName) {
        return client.fetch(AiProvider.class, providerName)
            .blockOptional()
            .orElseThrow(() -> new ModelNotFoundException(
                "Provider not found: " + providerName));
    }

    private String resolveApiKey(AiProvider provider) {
        var secretName = provider.getSpec().getApiKeySecretName();
        return secretResolver.resolveApiKey(secretName).block();
    }
}
