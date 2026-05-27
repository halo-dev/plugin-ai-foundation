package run.halo.aifoundation;

import java.beans.Transient;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {
    private List<String> inputs;
    private Integer dimensions;
    private Integer maxBatchSize;
    private Map<String, Object> providerOptions;
    private Map<String, Object> metadata;
    private Map<String, Object> context;
    private transient EmbeddingLifecycle lifecycle;
    private transient CancellationToken cancellationToken;
    private transient GenerationTimeouts timeouts;

    @Transient
    public EmbeddingLifecycle getLifecycle() {
        return lifecycle;
    }

    @Transient
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    @Transient
    public GenerationTimeouts getTimeouts() {
        return timeouts;
    }
}
