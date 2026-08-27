package run.halo.aifoundation.service.model;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.support.ProviderModelRef;

@Component
public class ModelRuntimeContextResolver {

    private final EffectiveParameterMappingResolver mappingResolver;
    private final ParameterMappingTemplateRegistry mappingTemplates;

    public ModelRuntimeContextResolver(EffectiveParameterMappingResolver mappingResolver,
        ParameterMappingTemplateRegistry mappingTemplates) {
        this.mappingResolver = mappingResolver;
        this.mappingTemplates = mappingTemplates;
    }

    public ModelRuntimeContext resolve(ModelResolution resolution) {
        var modelName = resolution.model().getMetadata().getName();
        var providerName = resolution.provider().getMetadata().getName();
        var mappings = new RuntimeParameterMappings(mappingResolver.resolve(resolution),
            mappingTemplates, modelName, providerName);
        var model = ProviderModelRef.from(resolution.model());
        return new ModelRuntimeContext(modelName, resolution.modelId(), providerName,
            resolution.providerTypeName(), model.adapterType(), resolution.providerType(), mappings);
    }
}
