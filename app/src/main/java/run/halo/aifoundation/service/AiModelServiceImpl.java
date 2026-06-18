package run.halo.aifoundation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.audit.AuditedEmbeddingModel;
import run.halo.aifoundation.service.audit.AuditedLanguageModel;
import run.halo.aifoundation.service.audit.AuditedRerankingModel;
import run.halo.aifoundation.service.audit.CallerPluginAuditRecorder;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.rerank.RerankingModelFactory;

@Component
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final AiModelResolver modelResolver;
    private final LanguageModelFactory languageModelFactory;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final RerankingModelFactory rerankingModelFactory;
    private final CallerPluginAuditRecorder callerPluginAuditRecorder;

    @Override
    public Mono<LanguageModel> languageModel() {
        return languageModel(null);
    }

    @Override
    public Mono<LanguageModel> languageModel(String modelName) {
        var resolvedModelName = StringUtils.hasText(modelName)
            ? Mono.just(modelName)
            : modelResolver.defaultLanguageModelName();
        return resolvedModelName
            .flatMap(name -> modelResolver.resolve(name, ModelType.LANGUAGE))
            .map(this::createLanguageModel);
    }

    @Override
    public Mono<EmbeddingModel> embeddingModel() {
        return embeddingModel(null);
    }

    @Override
    public Mono<EmbeddingModel> embeddingModel(String modelName) {
        var resolvedModelName = StringUtils.hasText(modelName)
            ? Mono.just(modelName)
            : modelResolver.defaultEmbeddingModelName();
        return resolvedModelName
            .flatMap(name -> modelResolver.resolve(name, ModelType.EMBEDDING))
            .map(this::createEmbeddingModel);
    }

    @Override
    public Mono<RerankingModel> rerankingModel() {
        return rerankingModel(null);
    }

    @Override
    public Mono<RerankingModel> rerankingModel(String modelName) {
        var resolvedModelName = StringUtils.hasText(modelName)
            ? Mono.just(modelName)
            : modelResolver.defaultRerankModelName();
        return resolvedModelName
            .flatMap(name -> modelResolver.resolve(name, ModelType.RERANK))
            .map(this::createRerankingModel);
    }

    private LanguageModel createLanguageModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.LANGUAGE);
        callerPluginAuditRecorder.recordModelResolution(context);
        return new AuditedLanguageModel(languageModelFactory.create(resolution), context,
            callerPluginAuditRecorder);
    }

    private EmbeddingModel createEmbeddingModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.EMBEDDING);
        callerPluginAuditRecorder.recordModelResolution(context);
        return new AuditedEmbeddingModel(embeddingModelFactory.create(resolution), context,
            callerPluginAuditRecorder);
    }

    private RerankingModel createRerankingModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.RERANK);
        callerPluginAuditRecorder.recordModelResolution(context);
        return new AuditedRerankingModel(rerankingModelFactory.create(resolution), context,
            callerPluginAuditRecorder);
    }

}
