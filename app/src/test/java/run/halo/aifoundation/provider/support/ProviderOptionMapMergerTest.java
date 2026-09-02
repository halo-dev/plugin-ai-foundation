package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderOptionMapMergerTest {

    @Test
    void mergesNestedObjectsAtLeafLevelWithoutMutatingSources() {
        var base = Map.<String, Object>of(
            "reasoning", Map.of("summary", "auto", "effort", "low"),
            "store", false);
        var overrides = Map.<String, Object>of(
            "reasoning", Map.of("effort", "high"));

        var merged = ProviderOptionMapMerger.merge(base, overrides);

        assertThat(nested(merged, "reasoning"))
            .containsEntry("summary", "auto")
            .containsEntry("effort", "high");
        assertThat(nested(base, "reasoning"))
            .containsEntry("summary", "auto")
            .containsEntry("effort", "low");
    }

    @Test
    void replacesNonObjectValuesInsteadOfGuessingTheirShape() {
        var merged = ProviderOptionMapMerger.merge(
            Map.of("reasoning", Map.of("effort", "low")),
            Map.of("reasoning", "disabled"));

        assertThat(merged).containsEntry("reasoning", "disabled");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> values, String field) {
        return (Map<String, Object>) values.get(field);
    }
}
