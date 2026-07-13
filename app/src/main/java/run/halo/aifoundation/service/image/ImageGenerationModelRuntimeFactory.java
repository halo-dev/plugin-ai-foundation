package run.halo.aifoundation.service.image;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;

@Component
public class ImageGenerationModelRuntimeFactory {

    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;

    public ImageGenerationModelRuntimeFactory(MediaResourcePolicy mediaResourcePolicy,
        ModelCapabilityMatcher capabilityMatcher) {
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.capabilityMatcher = capabilityMatcher;
    }

    public ImageGenerationModel create(ProviderImageGenerationClient client,
        ImageGenerationModelRuntimeConfiguration configuration) {
        return new ImageGenerationModelImpl(client, configuration.modelCapabilities(),
            mediaResourcePolicy, capabilityMatcher, configuration.context());
    }
}
