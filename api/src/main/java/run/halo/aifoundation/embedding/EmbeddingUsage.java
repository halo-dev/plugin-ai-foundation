package run.halo.aifoundation.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral token usage for an embedding request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingUsage {
    /**
     * Total token count reported for the embedding request.
     */
    private Integer tokens;
    /**
     * Raw provider usage object, retained for provider-specific diagnostics.
     */
    private Object raw;
}
