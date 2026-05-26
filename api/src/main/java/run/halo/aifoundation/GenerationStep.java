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
public class GenerationStep {
    private Integer stepIndex;
    private String text;
    private List<GenerationContentPart> content;
    private FinishReason finishReason;
    private String rawFinishReason;
    private LanguageModelUsage usage;
    private List<GenerationWarning> warnings;
    private GenerationRequestMetadata request;
    private GenerationResponseMetadata response;
    private Map<String, Object> providerMetadata;
}
