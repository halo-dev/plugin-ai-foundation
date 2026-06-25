package run.halo.aifoundation.service.image;

import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.service.model.ModelResolution;

public interface ImageGenerationModelFactory {

    ImageGenerationModel create(ModelResolution resolution);
}
