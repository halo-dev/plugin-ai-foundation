package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GenerationStepStartEvent {
    int stepIndex;
    GenerateTextRequest request;
    List<ModelMessage> messages;
    List<GenerationStep> previousSteps;
    List<ToolDefinition> tools;
    ToolChoice toolChoice;
    StopCondition stopWhen;
    Map<String, Map<String, Object>> providerOptions;
    GenerationTimeouts timeouts;
    Map<String, Object> metadata;
    Map<String, Object> context;
}
