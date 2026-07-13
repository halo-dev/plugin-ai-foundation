package run.halo.aifoundation.service.language;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.service.LanguageModelFactory;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.model.ModelRuntimeContextResolver;

@Slf4j
@Component
public class DefaultLanguageModelFactory implements LanguageModelFactory {

    private final ProviderClientCache providerClientCache;
    private final LanguageModelRuntimeFactory runtimeFactory;
    private final ModelCapabilityService modelCapabilityService;
    private final ModelRuntimeContextResolver runtimeContextResolver;

    public DefaultLanguageModelFactory(ProviderClientCache providerClientCache,
        LanguageModelRuntimeFactory runtimeFactory) {
        this(providerClientCache, runtimeFactory, new ModelCapabilityService(),
            new ModelRuntimeContextResolver(new EffectiveParameterMappingResolver(),
                new ParameterMappingTemplateRegistry()));
    }

    @Autowired
    public DefaultLanguageModelFactory(ProviderClientCache providerClientCache,
        LanguageModelRuntimeFactory runtimeFactory, ModelCapabilityService modelCapabilityService,
        ModelRuntimeContextResolver runtimeContextResolver) {
        this.providerClientCache = providerClientCache;
        this.runtimeFactory = runtimeFactory;
        this.modelCapabilityService = modelCapabilityService;
        this.runtimeContextResolver = runtimeContextResolver;
    }

    @Override
    public LanguageModel create(ModelResolution resolution) {
        log.info("Creating language model runtime: providerType={}, modelName={}, modelId={}",
            resolution.providerTypeName(), resolution.model().getMetadata().getName(),
            resolution.modelId());
        var chatModel = providerClientCache.getOrCreateChatModel(
            resolution.provider(), resolution.apiKey(), resolution.modelId());
        var capabilities = resolution.providerType() == null
            ? ModelCapabilities.empty()
            : modelCapabilityService.effectiveCapabilities(resolution.model(),
                resolution.providerType());
        var configuration = LanguageModelRuntimeConfiguration.from(
            runtimeContextResolver.resolve(resolution), capabilities);
        return runtimeFactory.create(chatModel, configuration);
    }
}
