package run.halo.aifoundation.service.model;

import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.aifoundation.exception.IncompatibleModelTypeException;
import run.halo.aifoundation.exception.ModelDisabledException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.exception.ProviderDisabledException;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.SecretResolver;
import run.halo.aifoundation.service.AiModelResolver;
import run.halo.aifoundation.setting.DefaultModelSlotStore;
import run.halo.aifoundation.setting.DefaultModelSlots;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class DefaultAiModelResolver implements AiModelResolver {

    private final ReactiveExtensionClient client;
    private final ProviderClientCache providerClientCache;
    private final SecretResolver secretResolver;
    private final DefaultModelSlotStore defaultModelSlotStore;

    @Override
    public Mono<ModelResolution> resolve(String modelName, ModelType expectedType) {
        return fetchAiModel(modelName)
            .flatMap(aiModel -> {
                if (!aiModel.getSpec().isEnabled()) {
                    return Mono.error(new ModelDisabledException(modelName));
                }
                var typeError = validateModelType(aiModel, expectedType, modelName);
                if (typeError != null) {
                    return Mono.error(typeError);
                }
                var providerName = aiModel.getSpec().getProviderName();
                return fetchProvider(providerName)
                    .flatMap(provider -> {
                        if (!provider.getSpec().isEnabled()) {
                            return Mono.error(new ProviderDisabledException(providerName));
                        }
                        return secretResolver.resolveApiKey(provider.getSpec().getApiKeySecretName())
                            .map(apiKey -> new ModelResolution(
                                aiModel,
                                provider,
                                providerClientCache.getProviderType(provider.getSpec().getProviderType()),
                                apiKey
                            ));
                    });
            });
    }

    @Override
    public Mono<String> defaultLanguageModelName() {
        return defaultSlotName("language", DefaultModelSlots::getLanguageModelName);
    }

    @Override
    public Mono<String> defaultEmbeddingModelName() {
        return defaultSlotName("embedding", DefaultModelSlots::getEmbeddingModelName);
    }

    private Mono<AiModel> fetchAiModel(String modelName) {
        return client.fetch(AiModel.class, modelName)
            .switchIfEmpty(Mono.error(new ModelNotFoundException(modelName)));
    }

    private Mono<AiProvider> fetchProvider(String providerName) {
        return client.fetch(AiProvider.class, providerName)
            .switchIfEmpty(Mono.error(new ModelNotFoundException(
                "Provider not found: " + providerName)));
    }

    private Throwable validateModelType(AiModel model, ModelType expectedType, String modelName) {
        var actualType = model.getSpec().getModelType();
        if (actualType == expectedType) {
            return null;
        }
        var actualValue = actualType != null ? actualType.getValue() : "null";
        return new IncompatibleModelTypeException(modelName, expectedType.getValue(), actualValue);
    }

    private Mono<String> defaultSlotName(String slotName,
        Function<DefaultModelSlots, String> extractor) {
        return defaultModelSlotStore.get()
            .flatMap(slots -> {
                var modelName = extractor.apply(slots);
                if (modelName == null || modelName.isBlank()) {
                    return Mono.error(new DefaultModelNotConfiguredException(slotName));
                }
                return Mono.just(modelName);
            });
    }
}
