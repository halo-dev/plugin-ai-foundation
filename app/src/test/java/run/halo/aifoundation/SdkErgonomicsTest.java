package run.halo.aifoundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SdkErgonomicsTest {

    @Test
    void jsonSchema_buildsDiscoverableObjectSchema() {
        var schema = JsonSchema.object()
            .description("Weather lookup input")
            .property("location", JsonSchema.string().description("City name"))
            .property("unit", JsonSchema.enumeration("celsius", "fahrenheit"))
            .property("days", JsonSchema.integer())
            .required("location")
            .build();

        assertThat(schema.toMap()).containsEntry("type", "object");
        assertThat(schema.toMap()).containsEntry("required", List.of("location"));
        var properties = (Map<?, ?>) schema.toMap().get("properties");
        assertThat(properties.containsKey("location")).isTrue();
        assertThat(properties.containsKey("unit")).isTrue();
        assertThat(properties.containsKey("days")).isTrue();
    }

    @Test
    void toolDefinition_acceptsTypedInputAndOutputSchemas() {
        var tool = ToolDefinition.builder()
            .name("weather")
            .description("Get weather")
            .inputSchema(JsonSchema.object()
                .property("location", JsonSchema.string())
                .required("location")
                .build())
            .outputSchema(JsonSchema.object()
                .property("temperature", JsonSchema.number())
                .required("temperature")
                .build())
            .build();

        assertThat(tool.getInputSchema()).containsEntry("type", "object");
        assertThat(tool.getOutputSchema()).containsEntry("type", "object");
    }

    @Test
    void outputSpec_acceptsTypedSchemas() {
        var objectOutput = OutputSpec.object(JsonSchema.object()
            .property("name", JsonSchema.string())
            .required("name")
            .build());
        var arrayOutput = OutputSpec.array(JsonSchema.string().build());

        assertThat(objectOutput.getType()).isEqualTo(OutputType.OBJECT);
        assertThat(objectOutput.getSchema()).containsEntry("type", "object");
        assertThat(arrayOutput.getType()).isEqualTo(OutputType.ARRAY);
        assertThat(arrayOutput.getElementSchema()).containsEntry("type", "string");
    }

    @Test
    void providerOptions_buildsNamespacedOptions() {
        var options = ProviderOptions.of(
            ProviderOptions.namespace("openai")
                .option("seed", 42)
                .option("dimensions", 512)
                .option("encodingFormat", "float")
                .build()
        );

        assertThat(options).containsOnlyKeys("openai");
        assertThat(options.get("openai"))
            .containsEntry("seed", 42)
            .containsEntry("dimensions", 512)
            .containsEntry("encodingFormat", "float");
    }

    @Test
    void messagePartBuilderRejectsInvalidShape() {
        assertThatThrownBy(() -> ModelMessagePart.builder()
            .type(PartType.TEXT)
            .text("hello")
            .toolName("weather")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("text message part has invalid fields");
    }

    @Test
    void generationContentBuilderRejectsInvalidShape() {
        assertThatThrownBy(() -> GenerationContentPart.builder()
            .type(PartType.TOOL_RESULT)
            .toolCallId("call_1")
            .toolName("weather")
            .text("not valid here")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("tool-result generation content part has invalid fields");
    }

    @Test
    void streamPartBuilderRejectsMissingRequiredFields() {
        assertThatThrownBy(() -> TextStreamPart.builder()
            .type(PartType.TEXT_DELTA)
            .delta("hello")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("text-delta stream part id must not be blank");
    }
}
