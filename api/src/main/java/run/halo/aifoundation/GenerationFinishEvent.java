package run.halo.aifoundation;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GenerationFinishEvent {
    GenerateTextResult result;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
