package run.halo.aifoundation.service.language;

import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

public record LanguageModelRuntimeConfiguration(
    ModelRuntimeContext context,
    LanguageModelProviderOptions providerOptions,
    ModelCapabilities modelCapabilities
) {

    public LanguageModelRuntimeConfiguration {
        providerOptions = providerOptions != null
            ? providerOptions : LanguageModelProviderOptions.defaults();
        var constraints = context != null ? context.parameterConstraints() : null;
        if (constraints != null) {
            var nativeStrictToolSchemas = providerOptions.nativeStrictToolSchemas()
                && !Boolean.FALSE.equals(constraints.getToolStrict());
            var structuredOutputSupport = Boolean.FALSE.equals(constraints.getStructuredOutput())
                ? StructuredOutputSupport.PROMPT_ONLY : providerOptions.structuredOutputSupport();
            providerOptions = new LanguageModelProviderOptions(
                providerOptions.reasoningHistorySupported(),
                providerOptions.streamToolCallsForReasoning(),
                providerOptions.requestHeadersSupported(), providerOptions.seedSupported(),
                nativeStrictToolSchemas, structuredOutputSupport,
                providerOptions.chatOptionsFactory(),
                providerOptions.toolCallingChatOptionsFactory(),
                providerOptions.structuredOutputChatOptionsFactory(),
                providerOptions.reasoningControlOptions(),
                providerOptions.reasoningContentExtractor(),
                providerOptions.nativeOptionsApplicator(),
                providerOptions.additionalMediaUrlSchemes());
        }
        modelCapabilities = modelCapabilities != null
            ? modelCapabilities : ModelCapabilities.empty();
    }

    public static LanguageModelRuntimeConfiguration from(ModelRuntimeContext context,
        ModelCapabilities modelCapabilities) {
        return new LanguageModelRuntimeConfiguration(context,
            context.providerDefinition().languageModelProviderOptions(context.adapterType()),
            modelCapabilities);
    }
}