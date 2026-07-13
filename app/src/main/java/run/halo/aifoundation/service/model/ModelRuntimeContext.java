package run.halo.aifoundation.service.model;

import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;

/**
 * Secret-free immutable configuration shared by capability-specific model runtimes.
 */
public record ModelRuntimeContext(
    String modelName,
    String modelId,
    String providerName,
    String providerType,
    AiProviderType providerDefinition,
    RuntimeParameterMappings parameterMappings
) {

    public ModelRuntimeContext {
        parameterMappings = parameterMappings != null
            ? parameterMappings : RuntimeParameterMappings.empty();
    }

    public static ModelRuntimeContext unresolved(String providerType) {
        return unresolved(providerType, null, null, RuntimeParameterMappings.empty());
    }

    public static ModelRuntimeContext unresolved(String providerType, String modelName,
        String providerName, RuntimeParameterMappings parameterMappings) {
        return unresolved(providerType, null, modelName, providerName, parameterMappings);
    }

    public static ModelRuntimeContext unresolved(String providerType, String modelId,
        String modelName, String providerName, RuntimeParameterMappings parameterMappings) {
        return new ModelRuntimeContext(modelName, modelId, providerName, providerType, null,
            parameterMappings);
    }
}
