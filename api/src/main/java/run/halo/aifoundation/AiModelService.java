package run.halo.aifoundation;

import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.rerank.RerankingModel;

/**
 * Cross-plugin entry point for resolving AI models managed by plugin-ai-foundation.
 *
 * <p>Consumer plugins should obtain this service through
 * {@code ExtensionGetter.getEnabledExtension(AiModelService.class)} because Halo plugins use
 * isolated Spring application contexts. The {@code modelName} parameters refer to
 * {@code AiModel.metadata.name}, not the provider type or provider-side model id.
 */
public interface AiModelService extends ExtensionPoint {

    /**
     * Resolves the configured default language model.
     *
     * @return a resolved language model
     */
    Mono<LanguageModel> languageModel();

    /**
     * Resolves an enabled language model by {@code AiModel.metadata.name}. When {@code modelName}
     * is {@code null} or blank, resolves the configured default language model.
     *
     * @param modelName Halo model resource name, or {@code null}/blank for the default slot
     * @return a resolved language model
     */
    Mono<LanguageModel> languageModel(String modelName);

    /**
     * Resolves the configured default embedding model.
     *
     * @return a resolved embedding model
     */
    Mono<EmbeddingModel> embeddingModel();

    /**
     * Resolves an enabled embedding model by {@code AiModel.metadata.name}. When {@code modelName}
     * is {@code null} or blank, resolves the configured default embedding model.
     *
     * @param modelName Halo model resource name, or {@code null}/blank for the default slot
     * @return a resolved embedding model
     */
    Mono<EmbeddingModel> embeddingModel(String modelName);

    /**
     * Resolves the configured default reranking model.
     *
     * @return a resolved reranking model
     */
    Mono<RerankingModel> rerankingModel();

    /**
     * Resolves an enabled reranking model by {@code AiModel.metadata.name}. When {@code modelName}
     * is {@code null} or blank, resolves the configured default reranking model.
     *
     * @param modelName Halo model resource name, or {@code null}/blank for the default slot
     * @return a resolved reranking model
     */
    Mono<RerankingModel> rerankingModel(String modelName);

    /**
     * Resolves the configured default image generation model.
     *
     * @return a resolved image generation model
     */
    Mono<ImageGenerationModel> imageGenerationModel();

    /**
     * Resolves an enabled image generation model by {@code AiModel.metadata.name}. When
     * {@code modelName} is {@code null} or blank, resolves the configured default image generation
     * model.
     *
     * @param modelName Halo model resource name, or {@code null}/blank for the default slot
     * @return a resolved image generation model
     */
    Mono<ImageGenerationModel> imageGenerationModel(String modelName);
}
