package run.halo.aifoundation.tool;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.schema.JsonSchema;

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
     * Approval policy evaluated after input schema validation and before executor invocation.
     */
    private ToolApprovalPolicy approvalPolicy;
    /**
     * Backpressured callback invoked before streamed input-start is exposed, or before a final-only
     * input is normalized. It is checked for cancellation before and after invocation, uses the
     * generation step timeout, and a failure terminates generation before approval or execution.
     */
    private transient ToolInputStartCallback onInputStart;
    /**
     * Backpressured callback invoked before each provider-native tool input delta is exposed.
     * It is serialized with all input events, checked for cancellation before and after invocation,
     * and uses the generation step timeout. A failure terminates generation. This callback is not
     * invoked for final-only or non-streaming tool calls.
     */
    private transient ToolInputDeltaCallback onInputDelta;
    /**
     * Backpressured callback invoked after normalized input is published as available and before
     * approval, external handoff, or server-side execution. It is checked for cancellation before
     * and after invocation, uses the generation step timeout, and a failure prevents every
     * downstream tool action.
     */
    private transient ToolInputAvailableCallback onInputAvailable;
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

        public ToolDefinitionBuilder requiresApproval(boolean requiresApproval) {
            this.approvalPolicy = requiresApproval
                ? ToolApprovalPolicy.always()
                : ToolApprovalPolicy.never();
            return this;
        }

        public ToolDefinitionBuilder needsApproval(boolean requiresApproval) {
            return requiresApproval(requiresApproval);
        }

        public ToolDefinitionBuilder approvalPredicate(ToolApprovalPredicate predicate) {
            this.approvalPolicy = ToolApprovalPolicy.dynamic(predicate);
            return this;
        }

        public ToolDefinitionBuilder needsApproval(ToolApprovalPredicate predicate) {
            return approvalPredicate(predicate);
        }
    }
}
