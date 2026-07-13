package run.halo.aifoundation.service.language;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.LanguageModel;
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

    public LanguageModel create(ChatModel chatModel,
        LanguageModelRuntimeConfiguration configuration) {
        var composition = LanguageModelRuntimeComposition.create(configuration, runtimeSupport,
            mediaResourcePolicy, capabilityMatcher);
        return new LanguageModelImpl(chatModel, composition, configuration.context());
    }
}
