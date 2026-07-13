package run.halo.aifoundation.service.rerank;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.rerank.RerankingModel;

@Component
public class RerankingModelRuntimeFactory {

    public RerankingModel create(ProviderRerankingClient client,
        RerankingModelRuntimeConfiguration configuration) {
        return new RerankingModelImpl(client, configuration.providerOptions(),
            configuration.context());
    }
}
