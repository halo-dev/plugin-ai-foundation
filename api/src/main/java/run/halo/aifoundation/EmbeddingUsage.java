package run.halo.aifoundation;

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
    private Integer tokens;
    private Object raw;
}
