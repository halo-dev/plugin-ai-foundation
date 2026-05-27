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
    private List<float[]> embeddings;
    private EmbeddingUsage usage;
    private EmbeddingResponseMetadata response;
    private List<EmbeddingWarning> warnings;
    private Map<String, Object> providerMetadata;
}
