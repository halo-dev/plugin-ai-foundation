package run.halo.aifoundation.rerank;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One ranked document result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {

    /**
     * Original index in {@link RerankRequest#getDocuments()}.
     */
    private int index;

    /**
     * Ranked document.
     */
    private RerankDocument document;

    /**
     * Provider score, when reported.
     */
    private Double score;

    /**
     * Sanitized provider metadata for this result.
     */
    private Map<String, Object> providerMetadata;
}
