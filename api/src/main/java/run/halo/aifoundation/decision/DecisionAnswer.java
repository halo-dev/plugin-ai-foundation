package run.halo.aifoundation.decision;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A typed decision. Probability and confidence are distinct quantities. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAnswer {
    private DecisionQuestionType type;
    /** YES_NO probability of yes, in [0, 1]; not a thresholded boolean. */
    private Double probability;
    /** Selected CHOICE option identifier. */
    private String choice;
    /** SCORE level index in [0, number of levels - 1], which may be fractional. */
    private Double score;
    /** Optional complete distribution over option identifiers or zero-based score level indices. */
    private Map<String, Double> probabilities;
    /** Optional provider-reported CHOICE/SCORE confidence in [0, 1]; semantics vary by provider. */
    private Double confidence;
    /** Optional SCORE level indices and descriptions. */
    private Map<String, String> legend;
}
