package run.halo.aifoundation;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A typed content part inside a {@link ModelMessage}.
 *
 * <p>Only a subset of fields is meaningful for each {@link #type}:
 * <ul>
 *   <li>{@link PartType#TEXT}: {@link #text}</li>
 *   <li>{@link PartType#REASONING}: {@link #text}, {@link #signature},
 *   {@link #providerOptions}</li>
 *   <li>{@link PartType#TOOL_CALL}: {@link #toolCallId}, {@link #toolName}, {@link #input}</li>
 *   <li>{@link PartType#TOOL_RESULT}: {@link #toolCallId}, {@link #toolName}, {@link #result}</li>
 *   <li>{@link PartType#TOOL_ERROR}: {@link #toolCallId}, {@link #toolName},
 *   {@link #errorText}</li>
 * </ul>
 *
 * <p>For normal callers, prefer the factory methods such as {@link #text(String)} and
 * {@link #toolResult(ToolResult)} over manually setting {@code type}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMessagePart {
    public static final String TYPE_TEXT = PartType.TEXT;
    public static final String TYPE_TOOL_CALL = PartType.TOOL_CALL;
    public static final String TYPE_TOOL_RESULT = PartType.TOOL_RESULT;
    public static final String TYPE_TOOL_ERROR = PartType.TOOL_ERROR;
    public static final String TYPE_REASONING = PartType.REASONING;

    /**
     * Part discriminator. Use values from {@link PartType}.
     */
    private String type;
    /**
     * Text content for {@link PartType#TEXT}.
     */
    private String text;
    /**
     * Optional provider signature for {@link PartType#REASONING}.
     */
    private String signature;
    /**
     * Provider or Halo generated tool call identifier. Tool result/error parts should echo the
     * same id so the model can correlate the response with the original tool call.
     */
    private String toolCallId;
    /**
     * Tool name as defined by {@link ToolDefinition#getName()}.
     */
    private String toolName;
    /**
     * Parsed tool arguments for {@link PartType#TOOL_CALL}.
     */
    private Map<String, Object> input;
    /**
     * Tool execution result for {@link PartType#TOOL_RESULT}. The value should be JSON
     * serializable.
     */
    private Object result;
    /**
     * Human-readable tool execution error for {@link PartType#TOOL_ERROR}.
     */
    private String errorText;
    /**
     * Provider-specific options scoped to this part. Most callers should prefer request-level
     * provider options unless a provider explicitly documents part-level behavior.
     */
    private Map<String, Object> providerOptions;

    /**
     * Creates a text message part.
     *
     * @param text non-blank text content
     * @return a text part
     */
    public static ModelMessagePart text(String text) {
        return ModelMessagePart.builder().type(TYPE_TEXT).text(text).build();
    }

    /**
     * Creates a persisted assistant reasoning part from visible reasoning text.
     *
     * <p>Reasoning parts are valid only in assistant messages and should be kept separate from
     * answer text. Provider-specific continuation fields belong in {@link #providerOptions}.
     */
    public static ModelMessagePart reasoning(String text) {
        return reasoning(ReasoningPart.builder().text(text).build());
    }

    /**
     * Creates a persisted assistant reasoning part with provider metadata.
     */
    public static ModelMessagePart reasoning(ReasoningPart reasoning) {
        return ModelMessagePart.builder()
            .type(TYPE_REASONING)
            .text(reasoning.getText())
            .signature(reasoning.getSignature())
            .providerOptions(reasoning.getProviderMetadata())
            .build();
    }

    /**
     * Creates a persisted assistant tool-call part from a model-produced {@link ToolCall}.
     *
     * <p>This is useful when a caller stores a conversation and later sends the assistant tool
     * call back together with a corresponding {@code TOOL} message.
     */
    public static ModelMessagePart toolCall(ToolCall toolCall) {
        return ModelMessagePart.builder()
            .type(TYPE_TOOL_CALL)
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .build();
    }

    /**
     * Creates a tool result part for a {@link ModelMessageRole#TOOL} message.
     *
     * <pre>{@code
     * ModelMessage toolMessage = ModelMessage.tool(List.of(
     *     ModelMessagePart.toolResult(toolResult)
     * ));
     * }</pre>
     */
    public static ModelMessagePart toolResult(ToolResult toolResult) {
        return ModelMessagePart.builder()
            .type(TYPE_TOOL_RESULT)
            .toolCallId(toolResult.getToolCallId())
            .toolName(toolResult.getToolName())
            .result(toolResult.getResult())
            .build();
    }

    /**
     * Creates a tool error part for a {@link ModelMessageRole#TOOL} message.
     */
    public static ModelMessagePart toolError(ToolError toolError) {
        return ModelMessagePart.builder()
            .type(TYPE_TOOL_ERROR)
            .toolCallId(toolError.getToolCallId())
            .toolName(toolError.getToolName())
            .errorText(toolError.getErrorText())
            .build();
    }
}
