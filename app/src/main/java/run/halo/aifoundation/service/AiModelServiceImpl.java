package run.halo.aifoundation.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.EmbeddingModel;
import run.halo.aifoundation.LanguageModel;
import run.halo.aifoundation.ModelInfo;
import run.halo.aifoundation.ModelNotFoundException;
import run.halo.aifoundation.ProviderDisabledException;
import run.halo.aifoundation.ProviderInfo;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.ProviderClientCache;
import run.halo.aifoundation.provider.SecretResolver;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;

    @Override
    public LanguageModel languageModel(String modelRef) {
        var parts = parseModelRef(modelRef);
        var providerName = parts[0];
        var modelId = parts[1];

        var aiModel = findAiModel(providerName, modelId);
        var provider = fetchProvider(providerName);

        if (!provider.getSpec().isEnabled()) {
            throw new ProviderDisabledException(providerName);
        }

        var apiKey = resolveApiKey(provider);
        var holder = providerClientCache.getOrCreate(provider, apiKey);
        var chatModel = holder.getAdapter().buildChatModel(modelId);

        return new LanguageModelImpl(chatModel, provider.getSpec().getProviderType());
    }

    @Override
    public EmbeddingModel embeddingModel(String modelRef) {
        var parts = parseModelRef(modelRef);
        var providerName = parts[0];
        var modelId = parts[1];

        var aiModel = findAiModel(providerName, modelId);
        var provider = fetchProvider(providerName);

        if (!provider.getSpec().isEnabled()) {
            throw new ProviderDisabledException(providerName);
        }

        var apiKey = resolveApiKey(provider);
        var holder = providerClientCache.getOrCreate(provider, apiKey);
        var adapter = holder.getAdapter();
        var springEmbeddingModel = adapter.buildEmbeddingModel(modelId);

        if (springEmbeddingModel == null) {
            throw new ModelNotFoundException(
                "Provider '" + providerName + "' does not support embeddings");
        }

        return new EmbeddingModelImpl(
            springEmbeddingModel,
            provider.getSpec().getProviderType(),
            adapter.maxEmbeddingsPerCall(),
            adapter.supportsParallelCalls()
        );
    }

    @Override
    public Mono<List<ModelInfo>> listModels() {
        return client.listAll(AiModel.class, new ListOptions(), Sort.unsorted())
            .map(model -> ModelInfo.builder()
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

    private String[] parseModelRef(String modelRef) {
        if (modelRef == null || !modelRef.contains("/")) {
            throw new ModelNotFoundException(modelRef);
        }
        var idx = modelRef.indexOf('/');
        var providerName = modelRef.substring(0, idx);
        var modelId = modelRef.substring(idx + 1);
        if (providerName.isBlank() || modelId.isBlank()) {
            throw new ModelNotFoundException(modelRef);
        }
        return new String[] {providerName, modelId};
    }

    private AiModel findAiModel(String providerName, String modelId) {
        return client.list(AiModel.class,
                model -> providerName.equals(model.getSpec().getProviderName())
                    && modelId.equals(model.getSpec().getModelId()),
                null)
            .next()
            .blockOptional()
            .orElseThrow(() -> new ModelNotFoundException(providerName + "/" + modelId));
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
