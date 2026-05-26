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
public class GenerateTextRequest {
    private String system;
    private String prompt;
    private List<ModelMessage> messages;
    private Integer maxOutputTokens;
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private List<String> stopSequences;
    private Map<String, Map<String, Object>> providerOptions;
}
