package run.halo.aifoundation.source;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retrieved context returned by a caller-provided retrieval component.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedContext {
    /**
     * Query used for retrieval.
     */
    private String query;
    /**
     * Retrieved sources in retriever-defined order.
     */
    private List<RetrievedSource> sources;
    /**
     * Caller-defined retrieval metadata. Values should be sanitized and serializable.
     */
    private Map<String, Object> metadata;
}
