package run.halo.aifoundation.service.image;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.service.model.ModelResolution;

@Component
public class DefaultImageGenerationModelFactory implements ImageGenerationModelFactory {

    private final ProviderClientCache providerClientCache;
    private final ModelCapabilityService modelCapabilityService;
    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;

    public DefaultImageGenerationModelFactory() {
        this(null, new ModelCapabilityService(), new MediaResourcePolicy(),
            new ModelCapabilityMatcher());
    }

    @Autowired
    public DefaultImageGenerationModelFactory(ProviderClientCache providerClientCache,
        ModelCapabilityService modelCapabilityService, MediaResourcePolicy mediaResourcePolicy,
        ModelCapabilityMatcher capabilityMatcher) {
        this.providerClientCache = providerClientCache;
        this.modelCapabilityService = modelCapabilityService;
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.capabilityMatcher = capabilityMatcher;
    }

    @Override
    public ImageGenerationModel create(ModelResolution resolution) {
        var adapter = AdapterType.firstFor(
            resolution.providerType().getSupportedAdapterTypes(),
            ModelType.IMAGE_GENERATION
        );
        if (adapter.isEmpty()) {
            return ImageGenerationModel.unsupported(
                resolution.model().getMetadata().getName(),
                resolution.provider().getMetadata().getName(),
                resolution.providerTypeName()
            );
        }
        var client = providerClientCache == null ? null
            : providerClientCache.getOrCreateImageGenerationClient(resolution.provider(),
                resolution.apiKey(), resolution.modelId());
        if (client == null) {
            return ImageGenerationModel.unsupported(
                resolution.model().getMetadata().getName(),
                resolution.provider().getMetadata().getName(),
                resolution.providerTypeName()
            );
        }
        var capabilities = modelCapabilityService.effectiveCapabilities(resolution.model(),
            resolution.providerType());
        return new ImageGenerationModelImpl(client, capabilities,
            resolution.model().getMetadata().getName(),
            resolution.provider().getMetadata().getName(),
            resolution.providerTypeName(), mediaResourcePolicy, capabilityMatcher);
    }
}
