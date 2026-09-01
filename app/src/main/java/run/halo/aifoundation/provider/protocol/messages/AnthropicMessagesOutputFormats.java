package run.halo.aifoundation.provider.protocol.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Maps portable structured-output options to the Messages {@code output_config} contract. */
public final class AnthropicMessagesOutputFormats {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT_TYPE = new TypeReference<>() {
    };

    private AnthropicMessagesOutputFormats() {
    }

    public static void applyJsonSchema(Map<String, Object> body,
        ChatCompletionsOptions options) {
        var responseFormat = options.getResponseFormat();
        if (responseFormat == null) {
            return;
        }
        if (responseFormat.getType() != ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA) {
            return;
        }
        var schema = readSchema(responseFormat.getJsonSchema());
        var outputConfig = mutableOutputConfig(body.get("output_config"));
        outputConfig.put("format", Map.of(
            "type", "json_schema",
            "schema", schema));
        body.put("output_config", outputConfig);
    }

    private static Map<String, Object> readSchema(String value) {
        if (value == null) {
            throw missingSchema();
        }
        if (value.isBlank()) {
            throw missingSchema();
        }
        try {
            return JSON_MAPPER.readValue(value, OBJECT_TYPE);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Failed to parse Messages JSON Schema", error);
        }
    }

    private static IllegalArgumentException missingSchema() {
        return new IllegalArgumentException(
            "Messages JSON Schema output requires a non-empty schema");
    }

    private static Map<String, Object> mutableOutputConfig(Object value) {
        var result = new LinkedHashMap<String, Object>();
        if (!(value instanceof Map<?, ?> existing)) {
            return result;
        }
        existing.forEach((key, fieldValue) -> result.put(String.valueOf(key), fieldValue));
        return result;
    }
}
