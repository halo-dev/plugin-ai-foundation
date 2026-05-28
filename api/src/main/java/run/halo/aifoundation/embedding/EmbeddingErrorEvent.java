package run.halo.aifoundation.embedding;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Lifecycle event emitted when an embedding request fails.
 *
 * <p>The error may be a provider exception, timeout, validation failure, or cancellation. Metadata
 * and context are copied from the original {@link EmbeddingRequest} for correlation.
 */
@Value
@Builder
public class EmbeddingErrorEvent {
    /**
     * Failure that stopped the embedding request.
     */
    Throwable error;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
