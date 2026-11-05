package run.halo.aifoundation.decision;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A closed question evaluated against the request's shared state. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionQuestion {
    private DecisionQuestionType type;
    private String instructions;
    /** CHOICE option identifiers and descriptions. */
    private Map<String, String> options;
    /** SCORE descriptions in ascending order; the first level has index zero. */
    private List<String> levels;
    /** Optional YES_NO criteria describing a positive answer. */
    private String yesCriteria;
    /** Optional YES_NO criteria describing a negative answer. */
    private String noCriteria;

    public static DecisionQuestion yesNo(String instructions) {
        return builder().type(DecisionQuestionType.YES_NO).instructions(instructions).build();
    }

    public static DecisionQuestion choice(String instructions, Map<String, String> options) {
        return builder().type(DecisionQuestionType.CHOICE).instructions(instructions)
            .options(options).build();
    }

    public static DecisionQuestion score(String instructions, List<String> levels) {
        return builder().type(DecisionQuestionType.SCORE).instructions(instructions)
            .levels(levels).build();
    }
}
