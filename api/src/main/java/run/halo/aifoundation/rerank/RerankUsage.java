package run.halo.aifoundation.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usage reported by a reranking provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankUsage {

    private Integer inputTokens;

    private Integer totalTokens;
}
