package run.halo.aifoundation.rerank;

import java.beans.Transient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationToken;

/**
 * Advanced request object for reranking candidate documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequest {

    /**
     * Query used to rank the candidate documents.
     */
    private String query;

    /**
     * Candidate documents. Returned result indexes refer to this ordered list.
     */
    private List<RerankDocument> documents;

    /**
     * Maximum number of ranked results to return.
     */
    private Integer topN;

    /** Request-scoped HTTP headers sent to providers when supported. */
    private Map<String, String> headers;


    /**
     * Caller metadata exposed to lifecycle callbacks. This data is not sent to providers.
     */
    private Map<String, Object> metadata;

    /**
     * Caller context exposed to lifecycle callbacks. This data is not sent to providers.
     */
    private Map<String, Object> context;

    /**
     * Request-scoped cancellation token.
     */
    private transient CancellationToken cancellationToken;

    /**
     * Request-scoped timeout settings.
     */
    private transient GenerationTimeouts timeouts;

    /** Retains the constructor published before request-scoped headers were introduced. */
    public RerankRequest(String query, List<RerankDocument> documents, Integer topN,
        Map<String, Object> metadata, Map<String, Object> context,
        CancellationToken cancellationToken, GenerationTimeouts timeouts) {
        this(query, documents, topN, null, metadata, context, cancellationToken, timeouts);
    }

    @Transient
    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    @Transient
    public GenerationTimeouts getTimeouts() {
        return timeouts;
    }

    public static class RerankRequestBuilder {
        public RerankRequestBuilder documents(List<RerankDocument> documents) {
            this.documents = documents;
            return this;
        }

        public RerankRequestBuilder documents(String... documents) {
            this.documents = documents == null ? null : Arrays.stream(documents)
                .map(RerankDocument::of)
                .toList();
            return this;
        }

    }
}
