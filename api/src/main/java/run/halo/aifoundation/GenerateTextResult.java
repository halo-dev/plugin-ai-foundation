package run.halo.aifoundation;

import java.util.Map;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTextResult {
    private String text;
    private Object output;
    private String outputText;
    private String reasoningText;
    private List<GenerationContentPart> content;
    private List<ReasoningPart> reasoning;
    private FinishReason finishReason;
    private String rawFinishReason;
    private LanguageModelUsage usage;
    private LanguageModelUsage totalUsage;
    private List<GenerationWarning> warnings;
    private GenerationRequestMetadata request;
    private GenerationResponseMetadata response;
    private List<GenerationStep> steps;
    private List<ToolCall> toolCalls;
    private List<ToolResult> toolResults;
    private List<ToolError> toolErrors;
    private Map<String, Object> providerMetadata;
}
