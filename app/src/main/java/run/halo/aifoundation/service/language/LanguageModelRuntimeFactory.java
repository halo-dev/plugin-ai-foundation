package run.halo.aifoundation.service.language;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;

@Component
public class LanguageModelRuntimeFactory {

    private final LanguageModelRuntimeSupport runtimeSupport;
    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;

    public LanguageModelRuntimeFactory(LanguageModelRuntimeSupport runtimeSupport) {
        this(runtimeSupport, new MediaResourcePolicy(), new ModelCapabilityMatcher());
    }

    @Autowired
    public LanguageModelRuntimeFactory(LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        this.runtimeSupport = runtimeSupport;
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.capabilityMatcher = capabilityMatcher;
    }

    public LanguageModel create(ChatModel chatModel, String providerType,
        LanguageModelProviderOptions providerOptions) {
        return new LanguageModelImpl(chatModel, compose(providerType, providerOptions));
    }

    public LanguageModel create(ChatModel chatModel, String providerType, String modelId,
        LanguageModelProviderOptions providerOptions) {
        return new LanguageModelImpl(chatModel, compose(providerType, modelId, providerOptions));
    }

    public LanguageModel create(ChatModel chatModel, String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, ModelCapabilities modelCapabilities,
        String modelName, String providerName) {
        return new LanguageModelImpl(chatModel, compose(providerType, modelId, providerOptions,
            modelCapabilities, modelName, providerName));
    }

    LanguageModelRuntimeComposition compose(String providerType,
        LanguageModelProviderOptions providerOptions) {
        return LanguageModelRuntimeComposition.create(providerType, providerOptions, runtimeSupport,
            mediaResourcePolicy, capabilityMatcher);
    }

    LanguageModelRuntimeComposition compose(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions) {
        return LanguageModelRuntimeComposition.create(providerType, modelId, providerOptions,
            runtimeSupport, mediaResourcePolicy, capabilityMatcher);
    }

    LanguageModelRuntimeComposition compose(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, ModelCapabilities modelCapabilities,
        String modelName, String providerName) {
        return LanguageModelRuntimeComposition.create(providerType, modelId, providerOptions,
            runtimeSupport, mediaResourcePolicy, capabilityMatcher, modelCapabilities, modelName,
            providerName);
    }
}
