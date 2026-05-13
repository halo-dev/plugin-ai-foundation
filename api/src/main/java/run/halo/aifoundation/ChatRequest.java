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
public class ChatRequest {
    private List<Message> messages;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Map<String, Object> providerOptions;
}
