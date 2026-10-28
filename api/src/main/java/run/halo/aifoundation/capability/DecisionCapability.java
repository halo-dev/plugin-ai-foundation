package run.halo.aifoundation.capability;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.decision.DecisionQuestionType;

/** Capabilities of a decision adapter. Null limits are unknown, not unlimited. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCapability {
    private List<DecisionQuestionType> questionTypes;
    private Boolean structuredState;
    private Integer maxChoiceOptions;
    private Integer maxScoreLevels;
}
