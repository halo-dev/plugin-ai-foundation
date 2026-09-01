package run.halo.aifoundation.embedding;

import java.beans.Transient;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationToken;

/**
 * Advanced request object for embedding text values or one multimodal content sequence.
 *
 * <p>For the common case, prefer {@link EmbeddingModel#embedQuery(String)}. Use this request when
 * you need batching controls, dimensions, request headers, lifecycle callbacks,
 * timeout control, or cancellation:
 *
 * <pre>{@code
 * EmbeddingRequest request = EmbeddingRequest.builder()
 *     .inputs(List.of("Halo CMS", "AI Foundation"))
 *     .dimensions(512)
 *     .maxBatchSize(16)
 *     .build();
 * }</pre>
 */
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
     * Typed parts that jointly produce one multimodal embedding.
     *
     * <p>This property and {@link #inputs} are mutually exclusive. Providers that do not expose a
     * native multimodal embedding endpoint reject requests that use this property.
     */
    private List<EmbeddingContent> contents;

    /** Optional natural-language instruction for providers with instruction-tuned embeddings. */
    private String instructions;

    /** Request a provider-native sparse embedding in response metadata when supported. */
    private Boolean includeSparseEmbedding;

    /**
     * Request per-modality embeddings in response metadata in addition to the joint multimodal
     * embedding when supported.
     */
    private Boolean includeModalityEmbeddings;

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
     * Request-scoped HTTP headers sent to providers when the selected provider adapter supports
     * dynamic request headers.
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

    /**
     * Caller metadata exposed to lifecycle callbacks. This data is not sent to providers.
     */
    private Map<String, Object> metadata;
    /**
     * Caller context exposed to lifecycle callbacks. This data is not sent to providers.
     */
    private Map<String, Object> context;

    /**
     * Request-scoped lifecycle callbacks. Transient because callbacks are Java runtime behavior,
     * not REST/OpenAPI payload.
     */
    private transient EmbeddingLifecycle lifecycle;

    /**
     * Request-scoped cancellation token. Use this as the Java cancellation signal.
     */
    private transient CancellationToken cancellationToken;

    /**
     * Request-scoped timeout settings.
     */
    private transient GenerationTimeouts timeouts;

    /**
     * Retains the constructor published before multimodal embedding fields were introduced.
     */
    public EmbeddingRequest(List<String> inputs, Integer dimensions, Integer maxBatchSize,
        Map<String, String> headers, Integer maxRetries, Integer maxParallelCalls,
        Map<String, Object> metadata, Map<String, Object> context,
        EmbeddingLifecycle lifecycle, CancellationToken cancellationToken,
        GenerationTimeouts timeouts) {
        this(inputs, null, null, null, null, dimensions, maxBatchSize, headers, maxRetries,
            maxParallelCalls, metadata, context, lifecycle, cancellationToken, timeouts);
    }

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
