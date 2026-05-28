package run.halo.aifoundation;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Provider-neutral Java SDK for embeddings.
 *
 * <p>Use simple methods for common query/document embedding, or {@link EmbeddingRequest} when
 * dimensions, batching, retries, provider options, lifecycle callbacks, cancellation, or timeout
 * controls are needed.
 */
public interface EmbeddingModel {

    /**
     * Embeds the provided inputs using default settings.
     */
    Mono<EmbeddingResponse> embed(List<String> inputs);

    /**
     * Embeds inputs using advanced request settings.
     */
    Mono<EmbeddingResponse> embed(EmbeddingRequest request);

    /**
     * Embeds a single query and returns the first vector.
     */
    Mono<float[]> embedQuery(String text);

    /**
     * Maximum provider batch size used by this model.
     */
    int maxEmbeddingsPerCall();

    /**
     * Whether this model implementation can execute provider batches concurrently.
     */
    boolean supportsParallelCalls();
}
