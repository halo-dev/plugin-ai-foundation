package run.halo.aifoundation.service;

import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.service.model.ModelResolution;

public interface EmbeddingModelFactory {

    EmbeddingModel create(ModelResolution resolution);
}
