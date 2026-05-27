package run.halo.aifoundation;

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
    private String code;
    private String message;
    private Map<String, Object> providerMetadata;
}
