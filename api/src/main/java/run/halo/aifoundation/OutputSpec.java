package run.halo.aifoundation;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral structured output specification for {@link GenerateTextRequest}.
 *
 * <p>The wire contract is JSON Schema based, using Java collection types. For Java callers that
 * want a Zod/Valibot-like experience, use {@link #object(Class)} or {@link #array(Class)} to
 * derive the JSON Schema from a record or simple POJO:
 *
 * <pre>{@code
 * record Recipe(String name, List<String> steps) {
 * }
 *
 * var request = GenerateTextRequest.builder()
 *     .prompt("Generate a lasagna recipe")
 *     .output(OutputSpec.object(Recipe.class))
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputSpec {
    /**
     * Output mode. Omitted output specs behave like {@link OutputType#TEXT}.
     */
    private OutputType type;
    /**
     * Optional output name used for provider guidance when supported.
     */
    private String name;
    /**
     * Optional output description used for provider guidance when supported.
     */
    private String description;
    /**
     * JSON Schema object for {@link OutputType#OBJECT}.
     */
    private Map<String, Object> schema;
    /**
     * Element JSON Schema for {@link OutputType#ARRAY}.
     */
    private Map<String, Object> elementSchema;
    /**
     * Allowed values for {@link OutputType#CHOICE}.
     */
    private List<String> choices;
    /**
     * Whether provider/local validation should prefer strict schema behavior when possible.
     */
    private Boolean strict;
    /**
     * Provider-specific output options grouped by provider namespace.
     */
    private Map<String, Map<String, Object>> providerOptions;
    /**
     * Transient Java target class used only by in-process Java callers.
     */
    private transient Class<?> outputClass;
    /**
     * Transient Java array element class used only by in-process Java callers.
     */
    private transient Class<?> elementClass;

    public static OutputSpec text() {
        return OutputSpec.builder().type(OutputType.TEXT).build();
    }

    public static OutputSpec json() {
        return OutputSpec.builder().type(OutputType.JSON).build();
    }

    public static OutputSpec object(Map<String, Object> schema) {
        return OutputSpec.builder().type(OutputType.OBJECT).schema(schema).build();
    }

    public static OutputSpec object(Class<?> outputClass) {
        return OutputSpec.builder()
            .type(OutputType.OBJECT)
            .schema(StructuredSchema.fromClass(outputClass))
            .outputClass(outputClass)
            .build();
    }

    public static OutputSpec array(Map<String, Object> elementSchema) {
        return OutputSpec.builder().type(OutputType.ARRAY).elementSchema(elementSchema).build();
    }

    public static OutputSpec array(Class<?> elementClass) {
        return OutputSpec.builder()
            .type(OutputType.ARRAY)
            .elementSchema(StructuredSchema.fromClass(elementClass))
            .elementClass(elementClass)
            .build();
    }

    public static OutputSpec choice(List<String> choices) {
        return OutputSpec.builder().type(OutputType.CHOICE).choices(choices).build();
    }
}
