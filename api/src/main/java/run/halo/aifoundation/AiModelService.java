package run.halo.aifoundation;

import java.util.List;
import reactor.core.publisher.Mono;

public interface AiModelService {

    LanguageModel languageModel(String modelName);

    EmbeddingModel embeddingModel(String modelName);

    Mono<List<ModelInfo>> listModels();

    Mono<List<ProviderInfo>> listProviders();
}
