package run.halo.aifoundation.provider.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.support.ModelParameterDomain;
import run.halo.aifoundation.provider.support.ModelType;

class ModelParameterCatalogTest {

    private final ModelParameterCatalog catalog = new ModelParameterCatalog();

    @Test
    void definesEveryParameterExactlyOnce() {
        assertThat(catalog.definitions())
            .extracting(ModelParameterDefinition::parameter)
            .containsExactlyInAnyOrder(ModelParameter.values())
            .doesNotHaveDuplicates();
        assertThat(catalog.definitions())
            .extracting(ModelParameterDefinition::domain)
            .contains(ModelParameterDomain.values());

        assertThat(catalog.definitions())
            .allSatisfy(definition -> {
                assertThat(definition.domain().getValue()).isNotBlank();
                assertThat(definition.field()).isNotBlank();
                assertThat(definition.displayName()).isNotBlank();
                assertThat(definition.description()).isNotBlank();
            });
    }

    @Test
    void accessorsRoundTripEveryParameterSelection() {
        for (var definition : catalog.definitions()) {
            var mappings = new ModelParameterMappings();
            var selection = new ModelParameterMappings.Selection();
            selection.setMode(ModelParameterMappings.Mode.UNSUPPORTED);

            definition.write(mappings, selection);

            assertThat(definition.read(mappings))
                .as(definition.parameter().name())
                .isSameAs(selection);
            assertThat(catalog.selections(mappings))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.definition()).isSameAs(definition);
                    assertThat(entry.selection()).isSameAs(selection);
                });
        }
    }

    @Test
    void filtersCompleteDefinitionsBySupportedModelTypes() {
        var supportedTypes = EnumSet.of(ModelType.EMBEDDING, ModelType.RERANK);

        assertThat(catalog.definitionsFor(supportedTypes))
            .extracting(ModelParameterDefinition::parameter)
            .containsExactly(ModelParameter.DIMENSIONS, ModelParameter.TOP_N);
    }

    @Test
    void ownsTheCuratedCommonParameterGrouping() {
        assertThat(catalog.definitions().stream()
            .filter(ModelParameterDefinition::common)
            .map(ModelParameterDefinition::parameter))
            .containsExactly(
                ModelParameter.MAX_OUTPUT_TOKENS,
                ModelParameter.TEMPERATURE,
                ModelParameter.TOP_P,
                ModelParameter.REASONING,
                ModelParameter.DIMENSIONS,
                ModelParameter.TOP_N,
                ModelParameter.IMAGE_COUNT,
                ModelParameter.IMAGE_SIZE,
                ModelParameter.ASPECT_RATIO
            );
    }

    @Test
    void modelTypeBelongsToDefinitions() {
        assertThat(catalog.definition(ModelParameter.REASONING).modelType())
            .isEqualTo(ModelType.LANGUAGE);
        assertThat(catalog.definition(ModelParameter.IMAGE_SIZE).modelType())
            .isEqualTo(ModelType.IMAGE_GENERATION);
    }
}
