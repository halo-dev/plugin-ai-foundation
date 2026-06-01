package run.halo.aifoundation.embedding;

import reactor.core.publisher.Mono;

/**
 * Request-scoped lifecycle callbacks for advanced embedding calls.
 *
 * <pre>{@code
 * var request = EmbeddingRequest.builder()
 *     .inputs(List.of("first", "second"))
 *     .lifecycle(new EmbeddingLifecycle() {
 *         @Override
 *         public Mono<Void> onFinish(EmbeddingFinishEvent event) {
 *             log.info("embedded {} item(s)", event.getEmbeddingsCount());
 *             return Mono.empty();
 *         }
 *     })
 *     .build();
 * }</pre>
 */
public interface EmbeddingLifecycle {

    default Mono<Void> onStart(EmbeddingStartEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onFinish(EmbeddingFinishEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onError(EmbeddingErrorEvent event) {
        return Mono.empty();
    }
}
