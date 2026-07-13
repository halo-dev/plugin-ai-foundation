package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.ModelParameterMappings;

class RuntimeParameterMappingsTest {

    @Test
    void appliesTemplateAndOwnsDiagnosticIdentity() {
        var effective = new EffectiveParameterMappings(Map.of(
            ModelParameter.TOP_N, mapping(ModelParameterMappings.Mode.TEMPLATE,
                "rerank.parameters.top-n"),
            ModelParameter.DIMENSIONS, mapping(ModelParameterMappings.Mode.UNSUPPORTED, null)
        ));
        var mappings = new RuntimeParameterMappings(effective,
            new ParameterMappingTemplateRegistry(), "model-a", "provider-a");
        var target = new ParameterMappingTarget();

        assertThat(mappings.apply(ModelParameter.TOP_N, 3, target)).isTrue();
        assertThat(target.parameters()).containsEntry("top_n", 3);
        assertThat(mappings.isUnsupported(ModelParameter.DIMENSIONS)).isTrue();
        assertThat(mappings.unsupportedDiagnostic(ModelParameter.DIMENSIONS).metadata())
            .containsEntry("modelName", "model-a")
            .containsEntry("providerName", "provider-a");
    }

    @Test
    void appliesAdministratorFieldOverrideWithinTemplatePlacement() {
        var effective = new EffectiveParameterMappings(Map.of(
            ModelParameter.TOP_N,
            new EffectiveParameterMappings.EffectiveMapping(
                ModelParameterMappings.Mode.TEMPLATE, "rerank.parameters.top-n",
                "ranking.limit", null, EffectiveParameterMappings.Source.MODEL)
        ));
        var mappings = new RuntimeParameterMappings(effective,
            new ParameterMappingTemplateRegistry(), "model-a", "provider-a");
        var target = new ParameterMappingTarget();

        assertThat(mappings.apply(ModelParameter.TOP_N, 5, target)).isTrue();
        assertThat(target.root()).isEmpty();
        assertThat(target.parameters()).containsEntry("ranking", Map.of("limit", 5));
    }

    @Test
    void appliesIndependentReasoningIntentFieldAndValue() {
        var enabled = new ModelParameterMappings.ReasoningValueMapping();
        enabled.setField("reasoning.mode");
        enabled.setValueType(ModelParameterMappings.ValueType.STRING);
        enabled.setValue("on");
        var reasoning = new ModelParameterMappings.ReasoningMapping();
        reasoning.setEnabled(enabled);
        var effective = new EffectiveParameterMappings(Map.of(
            ModelParameter.REASONING,
            new EffectiveParameterMappings.EffectiveMapping(
                ModelParameterMappings.Mode.TEMPLATE, "reasoning.effort", null, reasoning,
                EffectiveParameterMappings.Source.MODEL)
        ));
        var mappings = new RuntimeParameterMappings(effective,
            new ParameterMappingTemplateRegistry(), "model-a", "provider-a");
        var target = new ParameterMappingTarget();

        assertThat(mappings.canApplyReasoning(ReasoningOptions.enabled())).isTrue();
        assertThat(mappings.canApplyReasoning(ReasoningOptions.disabled())).isFalse();
        assertThat(mappings.applyReasoning(ReasoningOptions.enabled(), target)).isTrue();
        assertThat(target.root()).containsEntry("reasoning", Map.of("mode", "on"));
    }

    private EffectiveParameterMappings.EffectiveMapping mapping(
        ModelParameterMappings.Mode mode, String template) {
        return new EffectiveParameterMappings.EffectiveMapping(mode, template, null,
            EffectiveParameterMappings.Source.MODEL);
    }
}
