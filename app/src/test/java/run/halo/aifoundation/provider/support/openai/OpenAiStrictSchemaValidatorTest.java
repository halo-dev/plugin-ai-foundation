package run.halo.aifoundation.provider.support.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.exception.StructuredOutputSchemaException;
import run.halo.aifoundation.schema.OutputSpec;

class OpenAiStrictSchemaValidatorTest {

    @Test
    void acceptsClosedNestedSchemaWithNullableValue() {
        var schema = Map.<String, Object>of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                "title", Map.of("type", "string"),
                "metadata", Map.of(
                    "type", "object",
                    "additionalProperties", false,
                    "properties", Map.of(
                        "description", Map.of("type", List.of("string", "null"))
                    ),
                    "required", List.of("description")
                )
            ),
            "required", List.of("title", "metadata")
        );

        assertThat(OpenAiStrictSchemaValidator.validateAndBuildSchema(OutputSpec.object(schema)))
            .isEqualTo(schema);
    }

    @Test
    void rejectsArrayAndChoiceAsNativeStrictRootSchemas() {
        var elementSchema = Map.<String, Object>of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of("value", Map.of("type", "integer")),
            "required", List.of("value")
        );

        assertSchemaFailure(OutputSpec.array(elementSchema), "$.type");
        assertSchemaFailure(OutputSpec.choice(List.of("yes", "no")), "$.type");
    }

    @Test
    void rejectsObjectWithoutClosedAdditionalProperties() {
        var output = OutputSpec.object(Map.of(
            "type", "object",
            "properties", Map.of("title", Map.of("type", "string")),
            "required", List.of("title")
        ));

        assertSchemaFailure(output, "$.additionalProperties");
    }

    @Test
    void rejectsMissingAndUnexpectedRequiredProperties() {
        var output = OutputSpec.object(Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of("title", Map.of("type", "string")),
            "required", List.of("unexpected")
        ));

        assertSchemaFailure(output, "$.required");
    }

    @Test
    void rejectsNestedOpenObject() {
        var output = OutputSpec.object(Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                "metadata", Map.of(
                    "type", "object",
                    "properties", Map.of(),
                    "required", List.of()
                )
            ),
            "required", List.of("metadata")
        ));

        assertSchemaFailure(output, "$.properties.metadata.additionalProperties");
    }

    @Test
    void rejectsUnsupportedCompositionKeyword() {
        var output = OutputSpec.object(Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of(
                "value", Map.of("allOf", List.of(Map.of("type", "string")))
            ),
            "required", List.of("value")
        ));

        assertSchemaFailure(output, "$.properties.value.allOf");
    }

    @Test
    void rejectsRootTypeMismatch() {
        assertSchemaFailure(OutputSpec.object(Map.of("type", "string")), "$.type");
    }

    private void assertSchemaFailure(OutputSpec output, String expectedPath) {
        assertThatThrownBy(() -> OpenAiStrictSchemaValidator.validateAndBuildSchema(output))
            .isInstanceOfSatisfying(StructuredOutputSchemaException.class, error -> {
                assertThat(error.getValidationPath()).isEqualTo(expectedPath);
                assertThat(error.getMessage()).contains(expectedPath);
            });
    }
}
