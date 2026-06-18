package run.halo.aifoundation.rerank;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Non-fatal diagnostic from reranking execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankWarning {

    private String code;

    private String message;

    private Map<String, Object> providerMetadata;
}
