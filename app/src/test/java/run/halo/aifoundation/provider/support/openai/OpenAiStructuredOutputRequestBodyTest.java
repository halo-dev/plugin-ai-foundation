package run.halo.aifoundation.provider.support.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.exception.StructuredOutputSchemaException;
import run.halo.aifoundation.schema.OutputSpec;

class OpenAiStructuredOutputRequestBodyTest {

    @Test
    void omittedAndFalseStrictValuesRemainDisabled() {
        var schema = openObjectSchema();

        assertThat(jsonSchema(requestBody(OutputSpec.object(schema))).get("strict")).isEqualTo(false);
        assertThat(jsonSchema(requestBody(OutputSpec.builder()
            .type(run.halo.aifoundation.schema.OutputType.OBJECT)
            .schema(schema)
            .strict(false)
            .build())).get("strict")).isEqualTo(false);
    }

    @Test
    void strictClosedSchemaEnablesNativeStrictMode() {
        var output = OutputSpec.builder()
            .type(run.halo.aifoundation.schema.OutputType.OBJECT)
            .schema(closedObjectSchema())
            .strict(true)
            .build();

        var responseFormat = jsonSchema(requestBody(output));

        assertThat(responseFormat).containsEntry("strict", true)
            .containsEntry("name", "structured_output")
            .containsEntry("schema", closedObjectSchema());
    }

    @Test
    void nameAndDescriptionArePreserved() {
        var output = OutputSpec.builder()
            .type(run.halo.aifoundation.schema.OutputType.OBJECT)
            .name("link_metadata")
            .description("Recognized link metadata")
            .schema(openObjectSchema())
            .build();

        assertThat(jsonSchema(requestBody(output)))
            .containsEntry("name", "link_metadata")
            .containsEntry("description", "Recognized link metadata")
            .containsEntry("strict", false);
    }

    @Test
    void pluginLinksStyleOptionalSchemaRemainsNonStrict() {
        var schema = Map.<String, Object>of(
            "type", "object",
            "properties", Map.of(
                "isLinkApplication", Map.of("type", "boolean"),
                "url", Map.of("anyOf", List.of(
                    Map.of("type", "string"),
                    Map.of("type", "null")
                ))
            ),
            "required", List.of("isLinkApplication")
        );

        var responseFormat = jsonSchema(requestBody(OutputSpec.object(schema)));

        assertThat(responseFormat)
            .containsEntry("strict", false)
            .containsEntry("schema", schema);
    }

    @Test
    void arrayAndChoiceKeepTheirTopLevelShapesThroughPromptFallback() {
        var arrayBody = requestBody(OutputSpec.array(Map.of("type", "string")));
        var choiceBody = requestBody(OutputSpec.choice(List.of("friend", "tool")));

        assertThat(arrayBody).doesNotContainKey("response_format");
        assertThat(choiceBody).doesNotContainKey("response_format");
    }

    @Test
    void rawJsonUsesJsonObjectMode() {
        assertThat(requestBody(OutputSpec.json()).get("response_format"))
            .isEqualTo(Map.of("type", "json_object"));
    }

    @Test
    void invalidNameFailsLocally() {
        var output = OutputSpec.builder()
            .type(run.halo.aifoundation.schema.OutputType.OBJECT)
            .name("invalid name")
            .schema(openObjectSchema())
            .build();

        assertThatThrownBy(() -> requestBody(output))
            .isInstanceOfSatisfying(StructuredOutputSchemaException.class,
                error -> assertThat(error.getValidationPath()).isEqualTo("$.name"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestBody(OutputSpec output) {
        var request = GenerateTextRequest.builder().prompt("Generate output").output(output).build();
        var builder = OpenAiCompatibleChatOptions.builder()
            .baseUrl("http://localhost/v1")
            .apiKey("sk-test")
            .model("gpt-test");
        OpenAiStructuredOutputOptions.apply(builder, request);
        var options = builder.build();
        var model = new OpenAiCompatibleChatModel(options, WebClient.builder());
        var prompt = new Prompt(List.of(new UserMessage("Generate output")), options);
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonSchema(Map<String, Object> requestBody) {
        var responseFormat = (Map<String, Object>) requestBody.get("response_format");
        assertThat(responseFormat).containsEntry("type", "json_schema");
        return (Map<String, Object>) responseFormat.get("json_schema");
    }

    private Map<String, Object> openObjectSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of("title", Map.of("type", "string"))
        );
    }

    private Map<String, Object> closedObjectSchema() {
        return Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of("title", Map.of("type", "string")),
            "required", List.of("title")
        );
    }
}
