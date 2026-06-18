package run.halo.aifoundation.provider.support;

import lombok.Builder;
import lombok.Value;

/**
 * Provider capability flags used by the reranking runtime.
 */
@Value
@Builder
public class RerankingModelProviderOptions {

    boolean providerOptionsSupported;

    public static RerankingModelProviderOptions defaults() {
        return RerankingModelProviderOptions.builder()
            .providerOptionsSupported(false)
            .build();
    }
}
