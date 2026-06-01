package run.halo.aifoundation.embedding;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Non-fatal diagnostic emitted when an embedding setting is unsupported, ignored, or downgraded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingWarning {
    /**
     * Stable warning code intended for programmatic handling.
     */
    private String code;
    /**
     * Human-readable warning message for logs or developer diagnostics.
     */
    private String message;
    /**
     * Provider-specific details associated with this warning.
     */
    private Map<String, Object> providerMetadata;
}
