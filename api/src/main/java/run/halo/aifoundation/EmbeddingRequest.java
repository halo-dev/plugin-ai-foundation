package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {
    private List<String> inputs;
    private Integer dimensions;
    private Integer maxBatchSize;
    private Map<String, Object> providerOptions;
}
