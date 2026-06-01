package run.halo.aifoundation.embedding;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Lifecycle event emitted before an embedding request starts.
 */
@Value
@Builder
public class EmbeddingStartEvent {
    /**
     * Original embedding request.
     */
    EmbeddingRequest request;
    /**
     * Effective input list that will be embedded.
     */
    List<String> inputs;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
