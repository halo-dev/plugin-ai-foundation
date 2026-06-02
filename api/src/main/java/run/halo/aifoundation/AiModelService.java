package run.halo.aifoundation;

import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.embedding.EmbeddingModel;

/**
 * Cross-plugin entry point for resolving AI models managed by plugin-ai-foundation.
 *
 * <p>Consumer plugins should obtain this service through {@link AiServices#getModelService()}
 * because Halo plugins use isolated Spring application contexts. The {@code modelName} parameters
 * refer to {@code AiModel.metadata.name}, not the provider type or provider-side model id.
 */
public interface AiModelService extends ExtensionPoint {

    /**
     * Resolves an enabled language model by {@code AiModel.metadata.name}. When {@code modelName}
     * is {@code null} or blank, resolves the configured default language model.
     */
    Mono<LanguageModel> languageModel(String modelName);

    /**
     * Resolves an enabled embedding model by {@code AiModel.metadata.name}. When {@code modelName}
     * is {@code null} or blank, resolves the configured default embedding model.
     */
    Mono<EmbeddingModel> embeddingModel(String modelName);
}
