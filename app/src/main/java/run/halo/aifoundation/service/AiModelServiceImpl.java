package run.halo.aifoundation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.audit.AuditedEmbeddingModel;
import run.halo.aifoundation.service.audit.AuditedImageGenerationModel;
import run.halo.aifoundation.service.audit.AuditedLanguageModel;
import run.halo.aifoundation.service.audit.AuditedRerankingModel;
import run.halo.aifoundation.service.audit.CallerPluginAuditRecorder;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.image.ImageGenerationModelFactory;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.rerank.RerankingModelFactory;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AiModelServiceImpl implements AiModelService {

    private final AiModelResolver modelResolver;
    private final LanguageModelFactory languageModelFactory;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final RerankingModelFactory rerankingModelFactory;
    private final ImageGenerationModelFactory imageGenerationModelFactory;
    private final CallerPluginAuditRecorder callerPluginAuditRecorder;
    private final UsageStatisticsService usageStatisticsService;

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

    @Override
    public Mono<ImageGenerationModel> imageGenerationModel() {
        return imageGenerationModel(null);
    }

    @Override
    public Mono<ImageGenerationModel> imageGenerationModel(String modelName) {
        var resolvedModelName = StringUtils.hasText(modelName)
            ? Mono.just(modelName)
            : modelResolver.defaultImageGenerationModelName();
        return resolvedModelName
            .flatMap(name -> modelResolver.resolve(name, ModelType.IMAGE_GENERATION))
            .map(this::createImageGenerationModel);
    }

    private LanguageModel createLanguageModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.LANGUAGE);
        callerPluginAuditRecorder.recordModelResolution(context);
        var model = languageModelFactory.create(resolution);
        return new AuditedLanguageModel(model, context, callerPluginAuditRecorder,
            usageStatisticsService);
    }

    private EmbeddingModel createEmbeddingModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.EMBEDDING);
        callerPluginAuditRecorder.recordModelResolution(context);
        var model = embeddingModelFactory.create(resolution);
        return new AuditedEmbeddingModel(model, context, callerPluginAuditRecorder,
            usageStatisticsService);
    }

    private RerankingModel createRerankingModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.RERANK);
        callerPluginAuditRecorder.recordModelResolution(context);
        var model = rerankingModelFactory.create(resolution);
        return new AuditedRerankingModel(model, context, callerPluginAuditRecorder,
            usageStatisticsService);
    }

    private ImageGenerationModel createImageGenerationModel(ModelResolution resolution) {
        var context = ModelCallContext.from(resolution, ModelType.IMAGE_GENERATION);
        callerPluginAuditRecorder.recordModelResolution(context);
        var model = imageGenerationModelFactory.create(resolution);
        return new AuditedImageGenerationModel(model, context, callerPluginAuditRecorder,
            usageStatisticsService);
    }

}
