package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnsupportedParameterDiagnosticTest {

    @Test
    void allDomainsShareStableCodeAndResourceMetadata() {
        assertDiagnostic(ModelParameter.MAX_OUTPUT_TOKENS);
        assertDiagnostic(ModelParameter.DIMENSIONS);
        assertDiagnostic(ModelParameter.TOP_N);
        assertDiagnostic(ModelParameter.NEGATIVE_PROMPT);
    }

    private void assertDiagnostic(ModelParameter parameter) {
        var diagnostic = new UnsupportedParameterDiagnostic(parameter, "model-a", "provider-a");
        assertThat(diagnostic.languageWarning().getCode())
            .isEqualTo(UnsupportedParameterDiagnostic.CODE);
        assertThat(diagnostic.embeddingWarning().getCode())
            .isEqualTo(UnsupportedParameterDiagnostic.CODE);
        assertThat(diagnostic.rerankWarning().getCode())
            .isEqualTo(UnsupportedParameterDiagnostic.CODE);
        assertThat(diagnostic.imageWarning().getCode())
            .isEqualTo(UnsupportedParameterDiagnostic.CODE);
        assertThat(diagnostic.metadata()).containsEntry("parameter", parameter.name())
            .containsEntry("modelName", "model-a")
            .containsEntry("providerName", "provider-a");
    }
}
