package run.halo.aifoundation.rerank;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Provider-neutral Java SDK for reranking candidate documents.
 *
 * <p>Reranking is independent of retrieval storage. Callers can pass arbitrary text documents,
 * retrieved snippets, or any other candidate set and map ranked results back by the original
 * {@link RerankResult#getIndex()} values.
 */
public interface RerankingModel {

    /**
     * Reranks the provided documents using default settings.
     */
    default Mono<RerankResponse> rerank(String query, List<String> documents) {
        var request = RerankRequest.builder()
            .query(query)
            .documents(documents == null ? null : documents.stream()
                .map(RerankDocument::of)
                .toList())
            .build();
        return rerank(request);
    }

    /**
     * Reranks documents using advanced request settings.
     */
    Mono<RerankResponse> rerank(RerankRequest request);
}
