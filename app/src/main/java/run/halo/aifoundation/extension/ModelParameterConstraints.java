package run.halo.aifoundation.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** Model facts, not request defaults. Null means that no constraint was declared. */
@Data
public class ModelParameterConstraints {
    private Boolean temperature;
    private Boolean topP;
    private Boolean topK;
    private Boolean toolCalling;
    private Boolean structuredOutput;
    private Boolean toolStrict;
    private Boolean store;
    private Boolean streamUsage;
    private Boolean promptCacheKey;
    @Schema(description = "Maximum context tokens; not a generation default")
    private Long contextTokens;
    private Long inputTokens;
    private Long outputTokens;
    private List<String> toolChoices;
    /** Additional service tiers; absence from this list does not exclude native auto/default. */
    private List<String> serviceTiers;
    private Reasoning reasoning;

    @Data
    public static class Reasoning {
        private Boolean supported;
        private Boolean toggle;
        private List<String> efforts;
        private Long budgetMinimum;
        private Long budgetMaximum;
    }
}