package run.halo.aifoundation.rag;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.source.RetrievedContext;

/**
 * Caller-owned retrieval SPI used by RAG middleware.
 */
@FunctionalInterface
public interface RagRetriever {

    Mono<RetrievedContext> retrieve(RagRetrievalRequest request);
}
