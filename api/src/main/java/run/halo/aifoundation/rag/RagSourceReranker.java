package run.halo.aifoundation.rag;

import java.util.List;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.source.RetrievedSource;

/**
 * Optional source reranker used by RAG middleware.
 */
@FunctionalInterface
public interface RagSourceReranker {

    Mono<List<RetrievedSource>> rerank(RagSourceRerankRequest request);
}
