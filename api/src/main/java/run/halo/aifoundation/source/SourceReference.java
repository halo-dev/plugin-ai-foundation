package run.halo.aifoundation.source;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Display-safe reference to a source used or returned during generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {
    /**
     * Stable source identifier.
     */
    private String id;
    /**
     * Source type, such as url, post, page, file, or chunk.
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
     * Optional relevance score.
     */
    private Double score;
    /**
     * Sanitized display metadata. Full retrieved content should not be stored here by default.
     */
    private Map<String, Object> metadata;
}
