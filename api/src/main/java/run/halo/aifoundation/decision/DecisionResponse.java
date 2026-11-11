package run.halo.aifoundation.decision;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Answers keyed by the original question identifiers, with provider-reported model and usage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResponse {
    private String model;
    private Map<String, DecisionAnswer> answers;
    private DecisionUsage usage;
}
