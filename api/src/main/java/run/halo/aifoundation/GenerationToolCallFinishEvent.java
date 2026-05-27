package run.halo.aifoundation;

import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GenerationToolCallFinishEvent {
    int stepIndex;
    String toolCallId;
    String toolName;
    ToolResult result;
    ToolError error;
    Duration duration;
    Map<String, Object> providerMetadata;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
