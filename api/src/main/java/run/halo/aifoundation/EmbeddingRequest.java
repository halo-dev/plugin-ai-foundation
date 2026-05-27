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
    /**
     * Text values to embed. The returned embeddings preserve this order, even when the
     * implementation splits the request into several provider calls.
     */
    private List<String> inputs;

    /**
     * Provider-neutral dimensions override for models that support variable-size embeddings.
     */
    private Integer dimensions;

    /**
     * Caller-side maximum number of inputs per provider request. The implementation also applies
     * the provider's own maximum batch size.
     */
    private Integer maxBatchSize;

    /**
     * Provider-specific embedding settings grouped by provider namespace, matching the AI SDK
     * `providerOptions` shape.
     *
     * <pre>{@code
     * EmbeddingRequest request = EmbeddingRequest.builder()
     *     .inputs(List.of("Halo AI Foundation"))
     *     .providerOptions(Map.of(
     *         "openai", Map.of("dimensions", 512)
     *     ))
     *     .build();
     * }</pre>
     */
    private Map<String, Map<String, Object>> providerOptions;

    /**
     * Request-scoped HTTP headers for providers that support them.
     */
    private Map<String, String> headers;

    /**
     * Maximum retry count for retryable provider call failures. `0` disables retries.
     */
    private Integer maxRetries;

    /**
     * Maximum number of provider batch calls allowed to run concurrently.
     */
    private Integer maxParallelCalls;

    private Map<String, Object> metadata;
    private Map<String, Object> context;

    /**
     * Request-scoped lifecycle callbacks. Transient because callbacks are Java runtime behavior,
     * not REST/OpenAPI payload.
     */
    private transient EmbeddingLifecycle lifecycle;

    /**
     * Request-scoped cancellation token. Use this as the Java equivalent of AI SDK abort signals.
     */
    private transient CancellationToken cancellationToken;

    /**
     * Request-scoped timeout settings.
     */
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
