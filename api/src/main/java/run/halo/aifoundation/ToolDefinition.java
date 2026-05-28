package run.halo.aifoundation;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request-scoped tool definition that can be exposed to a language model.
 *
 * <p>Tools are provider-neutral at the API layer. Halo converts them to the underlying provider's
 * tool representation at runtime. The {@link #executor} is server-side code and is intentionally
 * {@code transient}; it is used when calling {@link LanguageModel#generateText(GenerateTextRequest)}
 * or {@link LanguageModel#streamText(GenerateTextRequest)} from Java, but is not part of the
 * serialized OpenAPI contract.
 *
 * <pre>{@code
 * ToolDefinition weather = ToolDefinition.builder()
 *     .name("weather")
 *     .description("Get current weather for a city")
 *     .inputSchema(JsonSchema.object()
 *         .property("location", JsonSchema.string().description("City name"))
 *         .required("location")
 *         .build())
 *     .executor(context -> Mono.just(Map.of(
 *         "location", context.getInput().get("location"),
 *         "temperature", 22
 *     )))
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    /**
     * Stable tool name visible to the model. Use letters, numbers, {@code _}, or {@code -}.
     */
    private String name;
    /**
     * Human-readable description that helps the model decide when to call the tool.
     */
    private String description;
    /**
     * JSON Schema object describing accepted tool arguments.
     */
    private Map<String, Object> inputSchema;
    /**
     * Optional JSON Schema object describing the server-side tool result.
     */
    private Map<String, Object> outputSchema;
    /**
     * Optional input examples that providers may use for tool guidance.
     */
    private java.util.List<Map<String, Object>> inputExamples;
    /**
     * Whether the provider should enforce strict schema matching when supported.
     */
    private Boolean strict;
    /**
     * Server-side tool implementation. Return values should be JSON serializable.
     */
    private transient ToolExecutor executor;

    /**
     * Creates a tool definition with typed schema helpers.
     */
    public static ToolDefinition of(String name, String description, JsonSchema inputSchema,
        ToolExecutor executor) {
        return ToolDefinition.builder()
            .name(name)
            .description(description)
            .inputSchema(inputSchema)
            .executor(executor)
            .build();
    }

    public static class ToolDefinitionBuilder {
        public ToolDefinitionBuilder inputSchema(Map<String, Object> schema) {
            this.inputSchema = schema;
            return this;
        }

        public ToolDefinitionBuilder inputSchema(JsonSchema schema) {
            this.inputSchema = schema != null ? schema.toMap() : null;
            return this;
        }

        public ToolDefinitionBuilder inputSchema(JsonSchema.Builder<?> schema) {
            this.inputSchema = schema != null ? schema.toMap() : null;
            return this;
        }

        public ToolDefinitionBuilder outputSchema(Map<String, Object> schema) {
            this.outputSchema = schema;
            return this;
        }

        public ToolDefinitionBuilder outputSchema(JsonSchema schema) {
            this.outputSchema = schema != null ? schema.toMap() : null;
            return this;
        }

        public ToolDefinitionBuilder outputSchema(JsonSchema.Builder<?> schema) {
            this.outputSchema = schema != null ? schema.toMap() : null;
            return this;
        }
    }
}
