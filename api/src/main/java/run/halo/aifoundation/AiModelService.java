package run.halo.aifoundation;

import java.util.List;
import org.pf4j.ExtensionPoint;
import reactor.core.publisher.Mono;

public interface AiModelService extends ExtensionPoint {

    Mono<LanguageModel> languageModel(String modelName);

    Mono<EmbeddingModel> embeddingModel(String modelName);

    Mono<LanguageModel> defaultLanguageModel();

    Mono<EmbeddingModel> defaultEmbeddingModel();

    Mono<List<ModelInfo>> listModels();

    Mono<List<ProviderInfo>> listProviders();
}
