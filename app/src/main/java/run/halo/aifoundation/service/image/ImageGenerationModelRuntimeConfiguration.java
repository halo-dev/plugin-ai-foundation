package run.halo.aifoundation.service.image;

import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

public record ImageGenerationModelRuntimeConfiguration(
    ModelRuntimeContext context,
    ModelCapabilities modelCapabilities
) {

    public ImageGenerationModelRuntimeConfiguration {
        modelCapabilities = modelCapabilities != null
            ? modelCapabilities : ModelCapabilities.empty();
    }
}
