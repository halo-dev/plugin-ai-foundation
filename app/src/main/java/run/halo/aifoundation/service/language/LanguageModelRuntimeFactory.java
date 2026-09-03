package run.halo.aifoundation.service.language;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.service.usage.UsageExecutionObserver;

@Component
public class LanguageModelRuntimeFactory {

    private final LanguageModelRuntimeSupport runtimeSupport;
    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;
    private final LanguageModelRuntimeProperties runtimeProperties;
    private final UsageExecutionObserver usageExecutionObserver;

    public LanguageModelRuntimeFactory(LanguageModelRuntimeSupport runtimeSupport) {
        this(runtimeSupport, new MediaResourcePolicy(), new ModelCapabilityMatcher(),
            new LanguageModelRuntimeProperties(), null);
    }

    public LanguageModelRuntimeFactory(LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher,
        LanguageModelRuntimeProperties runtimeProperties) {
        this(runtimeSupport, mediaResourcePolicy, capabilityMatcher, runtimeProperties, null);
    }

    @Autowired
    public LanguageModelRuntimeFactory(LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher,
        LanguageModelRuntimeProperties runtimeProperties,
        UsageExecutionObserver usageExecutionObserver) {
        this.runtimeSupport = runtimeSupport;
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.capabilityMatcher = capabilityMatcher;
        this.runtimeProperties = runtimeProperties;
        this.usageExecutionObserver = usageExecutionObserver;
    }

    public LanguageModel create(ChatModel chatModel,
        LanguageModelRuntimeConfiguration configuration) {
        var composition = LanguageModelRuntimeComposition.create(configuration, runtimeSupport,
            mediaResourcePolicy, capabilityMatcher);
        return new LanguageModelImpl(chatModel, composition, configuration.context(),
            runtimeProperties.getMaxSteps(), usageExecutionObserver);
    }
}
