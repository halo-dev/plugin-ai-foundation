package run.halo.aifoundation.service.rerank;

import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

public record RerankingModelRuntimeConfiguration(
    ModelRuntimeContext context,
    RerankingModelProviderOptions providerOptions
) {

    public RerankingModelRuntimeConfiguration {
        providerOptions = providerOptions != null
            ? providerOptions : RerankingModelProviderOptions.defaults();
    }

    public static RerankingModelRuntimeConfiguration from(ModelRuntimeContext context) {
        return new RerankingModelRuntimeConfiguration(context,
            context.providerDefinition().rerankingModelProviderOptions());
    }
}
