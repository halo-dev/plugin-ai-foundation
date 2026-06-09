package run.halo.aifoundation.service.language;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.service.LanguageModelFactory;
import run.halo.aifoundation.service.model.ModelResolution;

@Slf4j
@Component
public class DefaultLanguageModelFactory implements LanguageModelFactory {

    private final ProviderClientCache providerClientCache;
    private final LanguageModelRuntimeFactory runtimeFactory;

    public DefaultLanguageModelFactory(ProviderClientCache providerClientCache,
        LanguageModelRuntimeFactory runtimeFactory) {
        this.providerClientCache = providerClientCache;
        this.runtimeFactory = runtimeFactory;
    }

    @Override
    public LanguageModel create(ModelResolution resolution) {
        log.info("Creating language model runtime: providerType={}, modelName={}, modelId={}",
            resolution.providerTypeName(), resolution.model().getMetadata().getName(),
            resolution.modelId());
        var chatModel = providerClientCache.getOrCreateChatModel(
            resolution.provider(), resolution.apiKey(), resolution.modelId());
        var providerOptions = resolution.providerType() != null
            ? resolution.providerType().languageModelProviderOptions()
            : LanguageModelProviderOptions.defaults();
        return runtimeFactory.create(chatModel, resolution.providerTypeName(), resolution.modelId(),
            providerOptions);
    }
}
