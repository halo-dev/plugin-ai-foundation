package run.halo.aifoundation.service.rerank;

import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.model.ModelResolution;

public interface RerankingModelFactory {

    RerankingModel create(ModelResolution resolution);
}
