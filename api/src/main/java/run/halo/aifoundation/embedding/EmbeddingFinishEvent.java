package run.halo.aifoundation.embedding;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Lifecycle event emitted after embeddings are successfully generated.
 *
 * <p>{@link #getEmbeddingsCount()} is the number of vectors returned to the caller after any
 * batching has been merged back into the input order.
 */
@Value
@Builder
public class EmbeddingFinishEvent {
    /**
     * Final normalized embedding response.
     */
    EmbeddingResponse response;
    /**
     * Number of embedding vectors returned to the caller.
     */
    int embeddingsCount;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
