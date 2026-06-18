package run.halo.aifoundation.source;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Source returned by retrieval before it is packed into model context or exposed to UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedSource {
    /**
     * Stable source identifier.
     */
    private String id;
    /**
     * Caller-defined source type, such as url, post, page, file, or chunk.
     */
    private String sourceType;
    /**
     * Display title.
     */
    private String title;
    /**
     * Optional URL.
     */
    private String url;
    /**
     * Retrieved content used for prompt context. This is not exposed to UI by default.
     */
    private String content;
    /**
     * Relevance score when supplied by the retriever or reranker.
     */
    private Double score;
    /**
     * Sanitized caller metadata.
     */
    private Map<String, Object> metadata;
    /**
     * Whether this source should be included in packed model context.
     */
    private Boolean usedForContext;
    /**
     * Whether this source can be exposed as a public reference.
     */
    private Boolean visible;
}
