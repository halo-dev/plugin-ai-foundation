package run.halo.aifoundation.rerank;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider response metadata for reranking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResponseMetadata {

    private String id;

    private String model;

    private Map<String, Object> metadata;
}
