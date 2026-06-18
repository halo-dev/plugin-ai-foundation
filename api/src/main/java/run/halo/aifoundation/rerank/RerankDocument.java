package run.halo.aifoundation.rerank;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Candidate document submitted to a reranking model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankDocument {

    /**
     * Caller-defined id for mapping back to application objects.
     */
    private String id;

    /**
     * Text content used by the reranking model.
     */
    private String text;

    /**
     * Caller metadata. This data is not interpreted by AI Foundation.
     */
    private Map<String, Object> metadata;

    public static RerankDocument of(String text) {
        return RerankDocument.builder().text(text).build();
    }
}
