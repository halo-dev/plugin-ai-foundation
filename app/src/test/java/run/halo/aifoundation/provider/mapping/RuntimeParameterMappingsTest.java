package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;

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
        assertThat(target.appliedParameters()).containsExactly(ModelParameter.TOP_N);
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
        assertThat(target.appliedParameters()).containsExactly(ModelParameter.REASONING);
    }

    @Test
    void reasoningMappingsRemainExecutableRegardlessOfSource() {
        for (var source : EffectiveParameterMappings.Source.values()) {
            var mappings = runtimeReasoningMapping(source,
                ModelParameterMappings.Mode.TEMPLATE);
            assertThat(mappings.canApplyReasoning(
                ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))).isTrue();
        }
        assertThat(runtimeReasoningMapping(EffectiveParameterMappings.Source.BUILT_IN,
            ModelParameterMappings.Mode.UNSUPPORTED)
            .canApplyReasoning(ReasoningOptions.enabled())).isFalse();
        assertThat(RuntimeParameterMappings.empty()
            .canApplyReasoning(ReasoningOptions.enabled())).isFalse();
    }

    @Test
    void currentKimiDefaultsUseEffortWithoutPretendingThinkingCanBeDisabled() {
        var mapping = new EffectiveParameterMappings.EffectiveMapping(
            ModelParameterMappings.Mode.TEMPLATE, "reasoning.kimi", null, null,
            EffectiveParameterMappings.Source.BUILT_IN);
        var mappings = new RuntimeParameterMappings(new EffectiveParameterMappings(
            Map.of(ModelParameter.REASONING, mapping)), null, "model-a", "provider-a");
        var target = new ParameterMappingTarget();

        assertThat(mappings.canApplyReasoning(ReasoningOptions.disabled())).isFalse();
        assertThat(mappings.applyReasoning(
            ReasoningOptions.effort(ReasoningOptions.Effort.HIGH), target)).isTrue();
        assertThat(target.root()).containsEntry("reasoning_effort", "max");
    }

    @Test
    void compoundReasoningAppliesAllFieldsAtomically() {
        var primary = reasoningValue("thinking.type", "enabled",
            ModelParameterMappings.ValueType.STRING);
        var additional = reasoningValue("thinking.budget_tokens", "1024",
            ModelParameterMappings.ValueType.INTEGER);
        primary.setAdditionalValues(List.of(additional));
        var reasoning = new ModelParameterMappings.ReasoningMapping();
        reasoning.setHigh(primary);
        var mapping = new EffectiveParameterMappings.EffectiveMapping(
            ModelParameterMappings.Mode.TEMPLATE, "reasoning.effort", reasoning,
            EffectiveParameterMappings.Source.CATALOG);
        var runtime = new RuntimeParameterMappings(new EffectiveParameterMappings(
            Map.of(ModelParameter.REASONING, mapping)), null, "model", "provider");
        var target = new ParameterMappingTarget();
        runtime.applyReasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH), target);
        assertThat(target.root())
            .containsEntry("thinking", Map.of("type", "enabled", "budget_tokens", 1024));

        additional.setValue("invalid integer");
        var untouched = new ParameterMappingTarget();
        untouched.root().put("existing", true);
        assertThatThrownBy(() -> runtime.applyReasoning(
            ReasoningOptions.effort(ReasoningOptions.Effort.HIGH), untouched))
            .isInstanceOf(NumberFormatException.class);
        assertThat(untouched.root()).containsExactlyEntriesOf(Map.of("existing", true));
        assertThat(untouched.appliedParameters()).isEmpty();
    }

    @Test
    void malformedCompoundMappingsFailBeforeAnyTargetMutation() {
        for (var invalid : List.of("boolean", "overlap", "duplicate", "nested",
            "decimal", "missing")) {
            var primary = reasoningValue("thinking.type", "enabled",
                ModelParameterMappings.ValueType.STRING);
            var extra = reasoningValue("thinking.budget_tokens", "1024",
                ModelParameterMappings.ValueType.INTEGER);
            switch (invalid) {
                case "boolean" -> {
                    extra.setValueType(ModelParameterMappings.ValueType.BOOLEAN);
                    extra.setValue("yes");
                }
                case "overlap" -> extra.setField("thinking");
                case "duplicate" -> extra.setField("thinking.type");
                case "nested" -> extra.setAdditionalValues(List.of(primary));
                case "decimal" -> {
                    extra.setValueType(ModelParameterMappings.ValueType.DECIMAL);
                    extra.setValue("NaN");
                }
                case "missing" -> extra.setValue(null);
                default -> throw new IllegalStateException(invalid);
            }
            primary.setAdditionalValues(List.of(extra));
            var reasoning = new ModelParameterMappings.ReasoningMapping();
            reasoning.setEnabled(primary);
            var mapping = new EffectiveParameterMappings.EffectiveMapping(
                ModelParameterMappings.Mode.TEMPLATE, "reasoning.effort", reasoning,
                EffectiveParameterMappings.Source.MODEL);
            var runtime = new RuntimeParameterMappings(new EffectiveParameterMappings(
                Map.of(ModelParameter.REASONING, mapping)), null, "model", "provider");
            var target = new ParameterMappingTarget();
            assertThatThrownBy(() -> runtime.applyReasoning(ReasoningOptions.enabled(), target))
                .as(invalid).isInstanceOf(IllegalArgumentException.class);
            assertThat(target.root()).as(invalid).isEmpty();
            assertThat(target.appliedParameters()).isEmpty();
        }
    }

    private ModelParameterMappings.ReasoningValueMapping reasoningValue(String field,
        String value, ModelParameterMappings.ValueType type) {
        var mapping = new ModelParameterMappings.ReasoningValueMapping();
        mapping.setField(field);
        mapping.setValue(value);
        mapping.setValueType(type);
        return mapping;
    }

    private RuntimeParameterMappings runtimeReasoningMapping(
        EffectiveParameterMappings.Source source, ModelParameterMappings.Mode mode) {
        var mapping = new EffectiveParameterMappings.EffectiveMapping(
            mode, mode == ModelParameterMappings.Mode.TEMPLATE ? "reasoning.effort" : null,
            null, source);
        return new RuntimeParameterMappings(new EffectiveParameterMappings(
            Map.of(ModelParameter.REASONING, mapping)), null, "model-a", "provider-a");
    }

    private EffectiveParameterMappings.EffectiveMapping mapping(
        ModelParameterMappings.Mode mode, String template) {
        return new EffectiveParameterMappings.EffectiveMapping(mode, template, null,
            EffectiveParameterMappings.Source.MODEL);
    }
}