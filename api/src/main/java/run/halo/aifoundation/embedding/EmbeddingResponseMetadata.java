package run.halo.aifoundation.embedding;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Safe provider response metadata for an embedding request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponseMetadata {
    /**
     * Provider response id when one is available.
     */
    private String id;
    /**
     * Provider embedding model id reported by the response.
     */
    private String model;
    /**
     * Time when AI Foundation created this metadata object.
     */
    private Instant timestamp;
    /**
     * Provider response headers grouped by header name.
     */
    private Map<String, List<String>> headers;
    /**
     * Raw provider response body or provider-specific response object, when retained.
     */
    private Object body;
    /**
     * Additional provider or adapter metadata.
     */
    private Map<String, Object> metadata;
}
