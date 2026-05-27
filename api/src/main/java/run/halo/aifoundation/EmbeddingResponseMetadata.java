package run.halo.aifoundation;

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
    private String id;
    private String model;
    private Instant timestamp;
    private Map<String, List<String>> headers;
    private Object body;
    private Map<String, Object> metadata;
}
