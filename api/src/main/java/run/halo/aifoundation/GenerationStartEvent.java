package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GenerationStartEvent {
    GenerateTextRequest request;
    GenerationRequestMetadata requestMetadata;
    Map<String, Object> metadata;
    Map<String, Object> context;
    List<ToolDefinition> tools;
    ToolChoice toolChoice;
    StopCondition stopWhen;
    GenerationTimeouts timeouts;
}
