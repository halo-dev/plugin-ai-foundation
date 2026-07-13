package run.halo.aifoundation.service.language;

import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

public record LanguageModelRuntimeConfiguration(
    ModelRuntimeContext context,
    LanguageModelProviderOptions providerOptions,
    ModelCapabilities modelCapabilities
) {

    public LanguageModelRuntimeConfiguration {
        providerOptions = providerOptions != null
            ? providerOptions : LanguageModelProviderOptions.defaults();
        modelCapabilities = modelCapabilities != null
            ? modelCapabilities : ModelCapabilities.empty();
    }

    public static LanguageModelRuntimeConfiguration from(ModelRuntimeContext context,
        ModelCapabilities modelCapabilities) {
        return new LanguageModelRuntimeConfiguration(context,
            context.providerDefinition().languageModelProviderOptions(), modelCapabilities);
    }
}
