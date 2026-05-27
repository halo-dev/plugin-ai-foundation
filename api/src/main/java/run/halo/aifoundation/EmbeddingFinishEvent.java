package run.halo.aifoundation;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddingFinishEvent {
    EmbeddingResponse response;
    int embeddingsCount;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
