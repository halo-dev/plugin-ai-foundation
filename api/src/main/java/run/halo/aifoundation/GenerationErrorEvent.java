package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GenerationErrorEvent {
    Throwable error;
    Integer stepIndex;
    List<GenerationStep> steps;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
