package run.halo.aifoundation.provider.protocol.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

class AnthropicMessagesOutputFormatsTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsJsonSchemaAndPreservesExistingOutputConfiguration() {
        var body = new LinkedHashMap<String, Object>();
        body.put("output_config", Map.of("effort", "high"));
        var options = ChatCompletionsOptions.builder()
            .responseFormat(ChatCompletionsOptions.ResponseFormat.builder()
                .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema("{\"type\":\"object\",\"properties\":{}}")
                .build())
            .build();

        AnthropicMessagesOutputFormats.applyJsonSchema(body, options);

        var outputConfig = (Map<String, Object>) body.get("output_config");
        var format = (Map<String, Object>) outputConfig.get("format");
        assertThat(outputConfig).containsEntry("effort", "high");
        assertThat(format).containsEntry("type", "json_schema");
        assertThat((Map<String, Object>) format.get("schema"))
            .containsEntry("type", "object");
    }

    @Test
    void ignoresNonSchemaFormatsAndRejectsMalformedSchemas() {
        var body = new LinkedHashMap<String, Object>();
        var jsonObject = ChatCompletionsOptions.builder()
            .responseFormat(ChatCompletionsOptions.ResponseFormat.builder()
                .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_OBJECT)
                .build())
            .build();
        AnthropicMessagesOutputFormats.applyJsonSchema(body, jsonObject);
        assertThat(body).isEmpty();

        var malformed = ChatCompletionsOptions.builder()
            .responseFormat(ChatCompletionsOptions.ResponseFormat.builder()
                .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema("not-json")
                .build())
            .build();
        assertThatThrownBy(() -> AnthropicMessagesOutputFormats.applyJsonSchema(body, malformed))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("parse Messages JSON Schema");
    }
}
