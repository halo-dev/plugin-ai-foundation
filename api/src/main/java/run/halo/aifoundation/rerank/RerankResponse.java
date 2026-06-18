package run.halo.aifoundation.rerank;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model-independent reranking response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResponse {

    /**
     * Query used for ranking.
     */
    private String query;

    /**
     * Ranked results. Each result preserves the original input index.
     */
    private List<RerankResult> results;

    /**
     * Token usage when reported by the provider.
     */
    private RerankUsage usage;

    /**
     * Response-side metadata.
     */
    private RerankResponseMetadata response;

    /**
     * Non-fatal diagnostics emitted while serving the request.
     */
    private List<RerankWarning> warnings;

    /**
     * Sanitized provider metadata.
     */
    private Map<String, Object> providerMetadata;
}
