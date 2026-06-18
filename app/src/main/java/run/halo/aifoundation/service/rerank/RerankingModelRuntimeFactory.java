package run.halo.aifoundation.service.rerank;

import org.springframework.stereotype.Component;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.rerank.RerankingModel;

@Component
public class RerankingModelRuntimeFactory {

    public RerankingModel create(
        ProviderRerankingClient client,
        String providerType,
        RerankingModelProviderOptions providerOptions) {
        return new RerankingModelImpl(client, providerType, providerOptions);
    }
}
