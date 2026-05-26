package run.halo.aifoundation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LanguageModelUsage {
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Object raw;
}
