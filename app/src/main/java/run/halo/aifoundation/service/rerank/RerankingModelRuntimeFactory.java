package run.halo.aifoundation.service.rerank;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.usage.UsageExecutionObserver;

@Component
public class RerankingModelRuntimeFactory {

    private final UsageExecutionObserver usageExecutionObserver;

    public RerankingModelRuntimeFactory() {
        this(null);
    }

    @Autowired
    public RerankingModelRuntimeFactory(UsageExecutionObserver usageExecutionObserver) {
        this.usageExecutionObserver = usageExecutionObserver;
    }

    public RerankingModel create(ProviderRerankingClient client,
        RerankingModelRuntimeConfiguration configuration) {
        return new RerankingModelImpl(client, configuration.providerOptions(),
            configuration.context(), usageExecutionObserver);
    }
}
