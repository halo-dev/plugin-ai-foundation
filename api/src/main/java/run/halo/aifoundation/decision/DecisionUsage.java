package run.halo.aifoundation.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Reported token counts; absent counts stay unknown, rather than becoming zero. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionUsage {
    private Integer inputTokens;
    private Integer outputTokens;
}
