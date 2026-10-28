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
    /** SCORE's probability-weighted level index, which may be fractional. */
    private Double score;
    /** Distribution over option identifiers or zero-based score level indices. */
    private Map<String, Double> probabilities;
    /** Distribution-derived certainty for CHOICE/SCORE, absent for YES_NO. */
    private Double confidence;
    /** SCORE level indices and descriptions. */
    private Map<String, String> legend;
}
