package run.halo.aifoundation.provider.support;

import java.util.Map;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;

/**
 * Low-level provider adapter for reranking.
 */
@FunctionalInterface
public interface ProviderRerankingClient {

    Mono<RerankResponse> rerank(RerankRequest request);

    default Mono<RerankResponse> rerank(RerankRequest request, ParameterMappingTarget target) {
        return rerank(request);
    }

    default Mono<RerankResponse> rerank(RerankRequest request, ParameterMappingTarget target,
        Map<String, Object> nativeOptions) {
        if (nativeOptions == null || nativeOptions.isEmpty()) {
            return rerank(request, target);
        }
        return Mono.error(new IllegalStateException(
            "Reranking provider does not support configured native options"));
    }
}
