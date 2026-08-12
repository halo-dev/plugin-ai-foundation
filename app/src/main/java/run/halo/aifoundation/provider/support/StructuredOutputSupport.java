package run.halo.aifoundation.provider.support;

/**
 * Native structured-output behavior exposed by a provider adapter.
 */
public enum StructuredOutputSupport {
    /** Native JSON Schema response formats with an explicit strict flag. */
    JSON_SCHEMA,
    /** Native JSON Object mode without schema enforcement. */
    JSON_OBJECT,
    /** Prompt guidance and local validation only. */
    PROMPT_ONLY
}
