package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;

class ParameterMappingValidatorTest {

    private final ParameterMappingValidator validator =
        new ParameterMappingValidator(new ParameterMappingTemplateRegistry());

    @Test
    void acceptsAndNormalizesConstrainedFieldOverride() {
        var selection = temperatureSelection(" request.temperature ");

        validator.validateProvider(mappings(selection), providerType());

        assertThat(selection.getField()).isEqualTo("request.temperature");
    }

    @Test
    void rejectsUnsafeOrDeepFieldOverride() {
        assertThatThrownBy(() -> validator.validateProvider(
            mappings(temperatureSelection("request[temperature]")), providerType()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("field must be a dotted identifier");
        assertThatThrownBy(() -> validator.validateProvider(
            mappings(temperatureSelection("one.two.three.four.five")), providerType()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most four segments");
    }

    @Test
    void validatesEachReasoningIntentFieldAndTypedValue() {
        var selection = new ModelParameterMappings.Selection();
        selection.setMode(ModelParameterMappings.Mode.TEMPLATE);
        selection.setTemplate("reasoning.effort");
        var value = new ModelParameterMappings.ReasoningValueMapping();
        value.setField(" reasoning.level ");
        value.setValueType(ModelParameterMappings.ValueType.INTEGER);
        value.setValue(" invalid ");
        var reasoning = new ModelParameterMappings.ReasoningMapping();
        reasoning.setLow(value);
        selection.setReasoningMapping(reasoning);

        assertThatThrownBy(() -> validator.validateProvider(reasoningMappings(selection),
            providerType()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match INTEGER");
        assertThat(value.getField()).isEqualTo("reasoning.level");
        assertThat(value.getValue()).isEqualTo("invalid");
    }

    private AiProviderType providerType() {
        var providerType = mock(AiProviderType.class);
        when(providerType.getSupportedAdapterTypes()).thenReturn(List.of(AdapterType.OPENAI_CHAT));
        return providerType;
    }

    private ModelParameterMappings mappings(ModelParameterMappings.Selection selection) {
        var language = new ModelParameterMappings.LanguageMappings();
        language.setTemperature(selection);
        var mappings = new ModelParameterMappings();
        mappings.setLanguage(language);
        return mappings;
    }

    private ModelParameterMappings reasoningMappings(ModelParameterMappings.Selection selection) {
        var language = new ModelParameterMappings.LanguageMappings();
        language.setReasoning(selection);
        var mappings = new ModelParameterMappings();
        mappings.setLanguage(language);
        return mappings;
    }

    private ModelParameterMappings.Selection temperatureSelection(String field) {
        var selection = new ModelParameterMappings.Selection();
        selection.setMode(ModelParameterMappings.Mode.TEMPLATE);
        selection.setTemplate("chat.temperature");
        selection.setField(field);
        return selection;
    }
}
