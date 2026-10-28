package run.halo.aifoundation.capability;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.decision.DecisionQuestionType;

/**
 * Capabilities of a decision adapter. Null values mean unknown.
 * Output flags indicate supported information, not a guarantee that every response includes it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCapability {
    private List<DecisionQuestionType> questionTypes;
    private Boolean structuredState;
    private Integer maxChoiceOptions;
    private Integer maxScoreLevels;
    private Boolean choiceProbabilities;
    private Boolean choiceConfidence;
    private Boolean scoreProbabilities;
    private Boolean scoreConfidence;
    private Boolean scoreLegend;

    /** Retains the constructor used before optional output capabilities were introduced. */
    public DecisionCapability(List<DecisionQuestionType> questionTypes, Boolean structuredState,
        Integer maxChoiceOptions, Integer maxScoreLevels) {
        this(questionTypes, structuredState, maxChoiceOptions, maxScoreLevels,
            null, null, null, null, null);
    }
}
