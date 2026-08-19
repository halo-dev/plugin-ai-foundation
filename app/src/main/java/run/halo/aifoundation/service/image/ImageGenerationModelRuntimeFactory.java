package run.halo.aifoundation.service.image;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.service.usage.UsageExecutionObserver;

@Component
public class ImageGenerationModelRuntimeFactory {

    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;
    private final UsageExecutionObserver usageExecutionObserver;

    public ImageGenerationModelRuntimeFactory(MediaResourcePolicy mediaResourcePolicy,
        ModelCapabilityMatcher capabilityMatcher) {
        this(mediaResourcePolicy, capabilityMatcher, null);
    }

    @Autowired
    public ImageGenerationModelRuntimeFactory(MediaResourcePolicy mediaResourcePolicy,
        ModelCapabilityMatcher capabilityMatcher,
        UsageExecutionObserver usageExecutionObserver) {
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.capabilityMatcher = capabilityMatcher;
        this.usageExecutionObserver = usageExecutionObserver;
    }

    public ImageGenerationModel create(ProviderImageGenerationClient client,
        ImageGenerationModelRuntimeConfiguration configuration) {
        return new ImageGenerationModelImpl(client, configuration.modelCapabilities(),
            mediaResourcePolicy, capabilityMatcher, configuration.context(),
            usageExecutionObserver);
    }
}
