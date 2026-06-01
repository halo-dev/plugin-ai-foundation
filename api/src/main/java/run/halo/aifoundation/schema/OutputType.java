package run.halo.aifoundation.schema;

/**
 * Structured output mode for {@link OutputSpec}.
 */
public enum OutputType {
    /**
     * Plain text output. This is the default behavior when no output spec is provided.
     */
    TEXT,
    /**
     * A JSON object validated against a JSON Schema object.
     */
    OBJECT,
    /**
     * A JSON array whose elements are validated against an element schema.
     */
    ARRAY,
    /**
     * A string classification value selected from allowed choices.
     */
    CHOICE,
    /**
     * Any valid JSON value without schema validation.
     */
    JSON
}
