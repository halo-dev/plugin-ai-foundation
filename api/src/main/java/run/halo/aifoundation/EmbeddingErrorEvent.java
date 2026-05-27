package run.halo.aifoundation;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddingErrorEvent {
    Throwable error;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
