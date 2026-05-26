package run.halo.aifoundation;

import java.util.Map;
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
    private FinishReason finishReason;
    private String rawFinishReason;
    private LanguageModelUsage usage;
    private Map<String, Object> providerMetadata;
}
