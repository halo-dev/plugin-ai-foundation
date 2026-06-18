package run.halo.aifoundation.rag;

import reactor.core.publisher.Mono;

/**
 * Lightweight callbacks for observing RAG middleware execution.
 */
public interface RagLifecycle {

    default Mono<Void> onRetrievalStart(RagLifecycleEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onRetrievalFinish(RagLifecycleEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onRerankStart(RagLifecycleEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onRerankFinish(RagLifecycleEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onContextPacked(RagLifecycleEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onError(RagLifecycleEvent event) {
        return Mono.empty();
    }
}
