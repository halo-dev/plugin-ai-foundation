package run.halo.aifoundation;

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
    EmbeddingRequest request;
    List<String> inputs;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
