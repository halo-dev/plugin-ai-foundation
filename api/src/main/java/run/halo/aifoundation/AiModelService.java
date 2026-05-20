package run.halo.aifoundation;

import java.util.List;
import reactor.core.publisher.Mono;

public interface AiModelService {

    Mono<LanguageModel> languageModel(String modelName);

    Mono<EmbeddingModel> embeddingModel(String modelName);

    Mono<LanguageModel> defaultLanguageModel();

    Mono<EmbeddingModel> defaultEmbeddingModel();

    Mono<List<ModelInfo>> listModels();

    Mono<List<ProviderInfo>> listProviders();
}
