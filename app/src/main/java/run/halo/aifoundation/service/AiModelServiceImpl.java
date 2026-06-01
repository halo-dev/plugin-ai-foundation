package run.halo.aifoundation.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.model.ModelInfo;
import run.halo.aifoundation.model.ProviderInfo;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final ReactiveExtensionClient client;
    private final AiModelResolver modelResolver;
    private final LanguageModelFactory languageModelFactory;
    private final EmbeddingModelFactory embeddingModelFactory;

    @Override
    public Mono<LanguageModel> languageModel(String modelName) {
        return modelResolver.resolve(modelName, ModelType.LANGUAGE)
            .map(languageModelFactory::create);
    }

    @Override
    public Mono<EmbeddingModel> embeddingModel(String modelName) {
        return modelResolver.resolve(modelName, ModelType.EMBEDDING)
            .map(embeddingModelFactory::create);
    }

    @Override
    public Mono<LanguageModel> defaultLanguageModel() {
        return modelResolver.defaultLanguageModelName()
            .flatMap(this::languageModel);
    }

    @Override
    public Mono<EmbeddingModel> defaultEmbeddingModel() {
        return modelResolver.defaultEmbeddingModelName()
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

}
