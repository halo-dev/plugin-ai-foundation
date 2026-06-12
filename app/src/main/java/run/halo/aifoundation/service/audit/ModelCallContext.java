package run.halo.aifoundation.service.audit;

import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.model.ModelResolution;

public record ModelCallContext(ModelType modelType,
                               String modelName,
                               String providerName,
                               String providerType,
                               String modelId) {

    public static ModelCallContext from(ModelResolution resolution, ModelType modelType) {
        return new ModelCallContext(
            modelType,
            resolution.model().getMetadata().getName(),
            resolution.provider().getMetadata().getName(),
            resolution.providerTypeName(),
            resolution.modelId()
        );
    }
}
