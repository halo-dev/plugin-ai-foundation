package run.halo.aifoundation.provider.support;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;

/**
 * Low-level provider adapter for reranking.
 */
@FunctionalInterface
public interface ProviderRerankingClient {

    Mono<RerankResponse> rerank(RerankRequest request);
}
