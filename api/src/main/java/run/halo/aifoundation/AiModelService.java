package run.halo.aifoundation;

import java.util.List;
import reactor.core.publisher.Mono;

public interface AiModelService {

    LanguageModel languageModel(String modelRef);

    EmbeddingModel embeddingModel(String modelRef);

    Mono<List<ModelInfo>> listModels();

    Mono<List<ProviderInfo>> listProviders();
}
