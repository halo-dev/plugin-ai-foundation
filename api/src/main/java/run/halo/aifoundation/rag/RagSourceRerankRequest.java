package run.halo.aifoundation.rag;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.source.RetrievedSource;

/**
 * Request passed to an optional source reranker.
 */
@Value
@Builder
public class RagSourceRerankRequest {

    String query;

    List<RetrievedSource> sources;

    Integer topN;

    Map<String, Object> metadata;

    Map<String, Object> context;
}
