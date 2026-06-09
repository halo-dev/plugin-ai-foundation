package run.halo.aifoundation.service.language;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;

@Component
public class LanguageModelRuntimeFactory {

    private final LanguageModelRuntimeSupport runtimeSupport;

    public LanguageModelRuntimeFactory(LanguageModelRuntimeSupport runtimeSupport) {
        this.runtimeSupport = runtimeSupport;
    }

    public LanguageModel create(ChatModel chatModel, String providerType,
        LanguageModelProviderOptions providerOptions) {
        return new LanguageModelImpl(chatModel, compose(providerType, providerOptions));
    }

    public LanguageModel create(ChatModel chatModel, String providerType, String modelId,
        LanguageModelProviderOptions providerOptions) {
        return new LanguageModelImpl(chatModel, compose(providerType, modelId, providerOptions));
    }

    LanguageModelRuntimeComposition compose(String providerType,
        LanguageModelProviderOptions providerOptions) {
        return LanguageModelRuntimeComposition.create(providerType, providerOptions, runtimeSupport);
    }

    LanguageModelRuntimeComposition compose(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions) {
        return LanguageModelRuntimeComposition.create(providerType, modelId, providerOptions,
            runtimeSupport);
    }
}
