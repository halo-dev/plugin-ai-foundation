package run.halo.aifoundation;

import java.util.List;
import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Mono;

/**
 * Cross-plugin entry point for resolving AI models managed by plugin-ai-foundation.
 *
 * <p>Consumer plugins should obtain this service through {@link AiServices#getModelService()}
 * because Halo plugins use isolated Spring application contexts. The {@code modelName} parameters
 * refer to {@code AiModel.metadata.name}, not the provider type or provider-side model id.
 */
public interface AiModelService extends ExtensionPoint {

    /**
     * Resolves an enabled language model by {@code AiModel.metadata.name}.
     */
    Mono<LanguageModel> languageModel(String modelName);

    /**
     * Resolves an enabled embedding model by {@code AiModel.metadata.name}.
     */
    Mono<EmbeddingModel> embeddingModel(String modelName);

    /**
     * Resolves the configured default language model.
     */
    Mono<LanguageModel> defaultLanguageModel();

    /**
     * Resolves the configured default embedding model.
     */
    Mono<EmbeddingModel> defaultEmbeddingModel();

    /**
     * Lists configured model resources visible through this service.
     */
    Mono<List<ModelInfo>> listModels();

    /**
     * Lists configured provider resources visible through this service.
     */
    Mono<List<ProviderInfo>> listProviders();
}
