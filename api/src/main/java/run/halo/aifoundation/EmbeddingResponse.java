package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model-independent embedding response.
 *
 * <pre>{@code
 * model.embed(request).subscribe(response -> {
 *     List<float[]> vectors = response.getEmbeddings();
 *     EmbeddingUsage usage = response.getUsage();
 *     List<EmbeddingWarning> warnings = response.getWarnings();
 * });
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {
    /**
     * Embedding vectors in the same order as request inputs.
     */
    private List<float[]> embeddings;
    /**
     * Token usage when reported by the provider.
     */
    private EmbeddingUsage usage;
    /**
     * Response-side metadata.
     */
    private EmbeddingResponseMetadata response;
    /**
     * Non-fatal diagnostics emitted while serving the request.
     */
    private List<EmbeddingWarning> warnings;
    /**
     * Sanitized provider metadata.
     */
    private Map<String, Object> providerMetadata;
}
