package run.halo.aifoundation.service;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.model.ModelResolution;

public interface AiModelResolver {

    Mono<ModelResolution> resolve(String modelName, ModelType expectedType);

    Mono<String> defaultLanguageModelName();

    Mono<String> defaultEmbeddingModelName();

    Mono<String> defaultRerankModelName();
}
