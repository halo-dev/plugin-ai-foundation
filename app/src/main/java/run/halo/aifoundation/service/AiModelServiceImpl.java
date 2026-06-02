package run.halo.aifoundation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.provider.support.ModelType;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final AiModelResolver modelResolver;
    private final LanguageModelFactory languageModelFactory;
    private final EmbeddingModelFactory embeddingModelFactory;

    @Override
    public Mono<LanguageModel> languageModel(String modelName) {
        var resolvedModelName = StringUtils.hasText(modelName)
            ? Mono.just(modelName)
            : modelResolver.defaultLanguageModelName();
        return resolvedModelName
            .flatMap(name -> modelResolver.resolve(name, ModelType.LANGUAGE))
            .map(languageModelFactory::create);
    }

    @Override
    public Mono<EmbeddingModel> embeddingModel(String modelName) {
        var resolvedModelName = StringUtils.hasText(modelName)
            ? Mono.just(modelName)
            : modelResolver.defaultEmbeddingModelName();
        return resolvedModelName
            .flatMap(name -> modelResolver.resolve(name, ModelType.EMBEDDING))
            .map(embeddingModelFactory::create);
    }

}
