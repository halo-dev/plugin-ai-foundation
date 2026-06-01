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
import run.halo.aifoundation.options.ProviderOptions;

/**
 * Advanced request object for embedding one or more text values.
 *
 * <p>For the common case, prefer {@link EmbeddingModel#embedQuery(String)}. Use this request when
 * you need batching controls, dimensions, provider options, request headers, lifecycle callbacks,
 * timeout control, or cancellation:
 *
 * <pre>{@code
 * EmbeddingRequest request = EmbeddingRequest.builder()
 *     .inputs(List.of("Halo CMS", "AI Foundation"))
 *     .dimensions(512)
 *     .maxBatchSize(16)
 *     .providerOptions(ProviderOptions.of(
 *         ProviderOptions.namespace("openai")
 *             .option("encodingFormat", "float")
 *             .build()
 *     ))
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
     * Provider-neutral dimensions override for models that support variable-size embeddings.
     */
    private Integer dimensions;

    /**
     * Caller-side maximum number of inputs per provider request. The implementation also applies
     * the provider's own maximum batch size.
     */
    private Integer maxBatchSize;

    /**
     * Provider-specific embedding settings grouped by provider namespace, using the same provider namespace grouping as text generation
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

    public static class EmbeddingRequestBuilder {
        public EmbeddingRequestBuilder providerOptions(
            Map<String, Map<String, Object>> providerOptions) {
            this.providerOptions = providerOptions;
            return this;
        }

        public EmbeddingRequestBuilder providerOptions(ProviderOptions.NamespaceOptions... options) {
            this.providerOptions = ProviderOptions.of(options);
            return this;
        }
    }
}
