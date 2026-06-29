package run.halo.aifoundation.service.language;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.service.LanguageModelFactory;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.aifoundation.service.model.ModelResolution;

@Slf4j
@Component
public class DefaultLanguageModelFactory implements LanguageModelFactory {

    private final ProviderClientCache providerClientCache;
    private final LanguageModelRuntimeFactory runtimeFactory;
    private final ModelCapabilityService modelCapabilityService;

    public DefaultLanguageModelFactory(ProviderClientCache providerClientCache,
        LanguageModelRuntimeFactory runtimeFactory) {
        this(providerClientCache, runtimeFactory, new ModelCapabilityService());
    }

    @Autowired
    public DefaultLanguageModelFactory(ProviderClientCache providerClientCache,
        LanguageModelRuntimeFactory runtimeFactory, ModelCapabilityService modelCapabilityService) {
        this.providerClientCache = providerClientCache;
        this.runtimeFactory = runtimeFactory;
        this.modelCapabilityService = modelCapabilityService;
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
        var capabilities = resolution.providerType() == null
            ? ModelCapabilities.empty()
            : modelCapabilityService.effectiveCapabilities(resolution.model(),
                resolution.providerType());
        return runtimeFactory.create(chatModel, resolution.providerTypeName(), resolution.modelId(),
            providerOptions, capabilities, resolution.model().getMetadata().getName(),
            resolution.provider().getMetadata().getName());
    }
}
