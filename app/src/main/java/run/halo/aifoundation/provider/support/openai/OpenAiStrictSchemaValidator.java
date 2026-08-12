package run.halo.aifoundation.provider.support.openai;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import run.halo.aifoundation.exception.StructuredOutputSchemaException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.schema.OutputType;

/**
 * Validates the portable strict JSON Schema subset used by OpenAI-compatible adapters.
 */
final class OpenAiStrictSchemaValidator {

    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of(
        "allOf", "oneOf", "not", "if", "then", "else", "dependentRequired",
        "dependentSchemas", "patternProperties", "unevaluatedProperties"
    );

    private OpenAiStrictSchemaValidator() {
    }

    static Map<String, Object> validateAndBuildSchema(OutputSpec output) {
        var schema = buildSchema(output);
        validateRootType(schema, output.getType());
        validateSchema(schema, "$", output.getType());
        return schema;
    }

    static Map<String, Object> buildSchema(OutputSpec output) {
        return switch (output.getType()) {
            case OBJECT -> output.getSchema();
            case ARRAY -> {
                var schema = new LinkedHashMap<String, Object>();
                schema.put("type", "array");
                if (output.getElementSchema() != null) {
                    schema.put("items", output.getElementSchema());
                }
                yield schema;
            }
            case CHOICE -> {
                var schema = new LinkedHashMap<String, Object>();
                schema.put("type", "string");
                schema.put("enum", output.getChoices());
                yield schema;
            }
            default -> null;
        };
    }

    private static void validateRootType(Map<String, Object> schema, OutputType outputType) {
        if (schema == null) {
            throw failure(outputType, "$", "Strict structured output requires a JSON Schema");
        }
        if (outputType != OutputType.OBJECT || !hasType(schema.get("type"), "object")) {
            throw failure(outputType, "$.type",
                "Native strict structured output requires an object root schema");
        }
    }

    private static void validateSchema(Object value, String path, OutputType outputType) {
        if (!(value instanceof Map<?, ?> schema)) {
            throw failure(outputType, path, "Schema node must be an object");
        }

        for (var keyword : UNSUPPORTED_KEYWORDS) {
            if (schema.containsKey(keyword)) {
                throw failure(outputType, path + "." + keyword,
                    "Strict structured output does not support '" + keyword + "'");
            }
        }

        var objectSchema = hasType(schema.get("type"), "object")
            || schema.containsKey("properties");
        if (objectSchema) {
            validateObject(schema, path, outputType);
        }

        if (hasType(schema.get("type"), "array")) {
            if (!schema.containsKey("items")) {
                throw failure(outputType, path + ".items",
                    "Strict array schemas require 'items'");
            }
            validateSchema(schema.get("items"), path + ".items", outputType);
        }

        validateSchemaCollection(schema.get("anyOf"), path + ".anyOf", outputType);
        validateSchemaCollection(schema.get("prefixItems"), path + ".prefixItems", outputType);
        validateSchemaMap(schema.get("$defs"), path + ".$defs", outputType);
        validateSchemaMap(schema.get("definitions"), path + ".definitions", outputType);
    }

    private static void validateObject(Map<?, ?> schema, String path, OutputType outputType) {
        if (!Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            throw failure(outputType, path + ".additionalProperties",
                "Strict object schemas require additionalProperties to be false");
        }
        var propertiesValue = schema.get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)) {
            throw failure(outputType, path + ".properties",
                "Strict object schemas require a properties object");
        }
        var requiredValue = schema.get("required");
        if (!(requiredValue instanceof Collection<?> required)) {
            throw failure(outputType, path + ".required",
                "Strict object schemas require every property to be listed in required");
        }
        var requiredNames = required.stream().map(String::valueOf)
            .collect(java.util.stream.Collectors.toSet());
        var propertyNames = properties.keySet().stream().map(String::valueOf)
            .collect(java.util.stream.Collectors.toSet());
        if (!requiredNames.equals(propertyNames)) {
            throw failure(outputType, path + ".required",
                "Strict object schemas require required to contain exactly every property name");
        }
        for (var entry : properties.entrySet()) {
            validateSchema(entry.getValue(), path + ".properties." + entry.getKey(), outputType);
        }
    }

    private static void validateSchemaCollection(Object value, String path, OutputType outputType) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> schemas) || schemas.isEmpty()) {
            throw failure(outputType, path, "Schema alternatives must be a non-empty array");
        }
        for (int index = 0; index < schemas.size(); index++) {
            validateSchema(schemas.get(index), path + "[" + index + "]", outputType);
        }
    }

    private static void validateSchemaMap(Object value, String path, OutputType outputType) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> schemas)) {
            throw failure(outputType, path, "Schema definitions must be an object");
        }
        for (var entry : schemas.entrySet()) {
            validateSchema(entry.getValue(), path + "." + entry.getKey(), outputType);
        }
    }

    private static boolean hasType(Object type, String expected) {
        if (expected.equals(type)) {
            return true;
        }
        return type instanceof Collection<?> types && types.contains(expected);
    }

    private static StructuredOutputSchemaException failure(OutputType outputType, String path,
        String message) {
        return new StructuredOutputSchemaException(message + " at " + path, outputType, path);
    }
}
