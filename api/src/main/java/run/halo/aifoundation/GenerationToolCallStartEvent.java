package run.halo.aifoundation;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GenerationToolCallStartEvent {
    int stepIndex;
    String toolCallId;
    String toolName;
    Map<String, Object> input;
    Map<String, Object> providerMetadata;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
