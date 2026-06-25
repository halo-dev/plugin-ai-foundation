package run.halo.aifoundation.image;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;

class UnsupportedImageGenerationModel implements ImageGenerationModel {

    private final String modelName;
    private final String providerName;
    private final String providerType;

    UnsupportedImageGenerationModel(String modelName, String providerName, String providerType) {
        this.modelName = modelName;
        this.providerName = providerName;
        this.providerType = providerType;
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
        return Mono.error(new UnsupportedModelCapabilityException(modelName, providerName,
            providerType, "imageGeneration", true, false));
    }
}
