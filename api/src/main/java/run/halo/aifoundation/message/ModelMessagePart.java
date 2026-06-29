package run.halo.aifoundation.message;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

/**
 * A typed content part inside a {@link ModelMessage}.
 *
 * <p>Only a subset of fields is meaningful for each {@link #type}:
 * <ul>
 *   <li>{@link PartType#TEXT}: {@link #text}</li>
 *   <li>{@link PartType#IMAGE}: {@link #media}</li>
 *   <li>{@link PartType#FILE}: {@link #media}</li>
 *   <li>{@link PartType#REASONING}: {@link #text}, {@link #signature},
 *   {@link #providerOptions}</li>
 *   <li>{@link PartType#TOOL_CALL}: {@link #toolCallId}, {@link #toolName}, {@link #input}</li>
 *   <li>{@link PartType#TOOL_RESULT}: {@link #toolCallId}, {@link #toolName}, {@link #result}</li>
 *   <li>{@link PartType#TOOL_ERROR}: {@link #toolCallId}, {@link #toolName},
 *   {@link #errorText}</li>
 *   <li>{@link PartType#TOOL_APPROVAL_REQUEST}: {@link #approvalId}, {@link #toolCallId},
 *   {@link #toolName}, {@link #input}, {@link #stepIndex}</li>
 * </ul>
 *
 * <p>For normal callers, prefer the factory methods such as {@link #text(String)} and
 * {@link #toolResult(ToolResult)} over manually setting {@code type}.
 */
@Data
@Builder(buildMethodName = "uncheckedBuild")
@NoArgsConstructor
@AllArgsConstructor
public class ModelMessagePart {
                    
    /**
     * Part discriminator. Use values from {@link PartType}.
     */
    private String type;
    /**
     * Text content for {@link PartType#TEXT}.
     */
    private String text;
    /**
     * Caller-provided image or file content for {@link PartType#IMAGE} and {@link PartType#FILE}.
     */
    private DataContent media;
    /**
     * Optional provider signature for {@link PartType#REASONING}.
     */
    private String signature;
    /**
     * Approval id for tool approval request/response parts.
     */
    private String approvalId;
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
     * Zero-based model invocation step that produced a tool approval request.
     */
    private Integer stepIndex;
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
     * Whether a tool approval response approved execution.
     */
    private Boolean approved;
    /**
     * Optional approval or denial reason.
     */
    private String reason;
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
        return ModelMessagePart.builder().type(PartType.TEXT).text(text).build();
    }

    /**
     * Creates an image input message part.
     *
     * <p>Image parts are valid for user messages and assistant history. The runtime validates the
     * selected model's image input capability before provider invocation.
     *
     * @param media caller-provided image content
     * @return an image input part
     */
    public static ModelMessagePart image(DataContent media) {
        return ModelMessagePart.builder()
            .type(PartType.IMAGE)
            .media(media)
            .build();
    }

    /**
     * Creates an image input message part from a provider-native URL.
     *
     * <p>The URL is not downloaded by AI Foundation. The target model must support native URL
     * input.
     *
     * @param url provider-native image URL
     * @return an image input part
     */
    public static ModelMessagePart imageUrl(String url) {
        return image(DataContent.url(url));
    }

    /**
     * Creates an image input message part from base64 data.
     *
     * @param base64Data base64 encoded image bytes without a data URL prefix
     * @param mediaType image media type such as {@code image/png}
     * @return an image input part
     */
    public static ModelMessagePart imageData(String base64Data, String mediaType) {
        return image(DataContent.data(base64Data, mediaType));
    }

    /**
     * Creates a generic file input message part.
     *
     * <p>Use this for non-image media such as PDFs or text files. Image media should normally use
     * {@link #image(DataContent)} so providers receive the most specific input semantics.
     *
     * @param media caller-provided file content
     * @return a file input part
     */
    public static ModelMessagePart file(DataContent media) {
        return ModelMessagePart.builder()
            .type(PartType.FILE)
            .media(media)
            .build();
    }

    /**
     * Creates a persisted assistant reasoning part from visible reasoning text.
     *
     * <p>Reasoning parts are valid only in assistant messages and should be kept separate from
     * answer text. Provider-specific continuation fields belong in {@link #providerOptions}.
     *
     * @param text visible reasoning text
     * @return an assistant reasoning part
     */
    public static ModelMessagePart reasoning(String text) {
        return reasoning(ReasoningPart.builder().text(text).build());
    }

    /**
     * Creates a persisted assistant reasoning part with provider metadata.
     *
     * @param reasoning reasoning text and provider metadata
     * @return an assistant reasoning part
     */
    public static ModelMessagePart reasoning(ReasoningPart reasoning) {
        return ModelMessagePart.builder()
            .type(PartType.REASONING)
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
     *
     * @param toolCall model-produced tool call
     * @return an assistant tool-call part
     */
    public static ModelMessagePart toolCall(ToolCall toolCall) {
        return ModelMessagePart.builder()
            .type(PartType.TOOL_CALL)
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .build();
    }

    /**
     * Creates a persisted assistant approval request part from a pending tool approval.
     *
     * @param request pending tool approval request
     * @return an assistant tool approval request part
     */
    public static ModelMessagePart toolApprovalRequest(ToolApprovalRequest request) {
        return ModelMessagePart.builder()
            .type(PartType.TOOL_APPROVAL_REQUEST)
            .approvalId(request.getApprovalId())
            .toolCallId(request.getToolCallId())
            .toolName(request.getToolName())
            .input(request.getInput())
            .stepIndex(request.getStepIndex())
            .providerOptions(request.getProviderMetadata())
            .build();
    }

    /**
     * Creates a tool message part that answers a prior approval request.
     *
     * @param response caller approval or denial response
     * @return a tool approval response part
     */
    public static ModelMessagePart toolApprovalResponse(ToolApprovalResponse response) {
        return ModelMessagePart.builder()
            .type(PartType.TOOL_APPROVAL_RESPONSE)
            .approvalId(response.getApprovalId())
            .toolCallId(response.getToolCallId())
            .toolName(response.getToolName())
            .approved(response.getApproved())
            .reason(response.getReason())
            .providerOptions(response.getProviderMetadata())
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
     *
     * @param toolResult tool execution result
     * @return a tool result part
     */
    public static ModelMessagePart toolResult(ToolResult toolResult) {
        return ModelMessagePart.builder()
            .type(PartType.TOOL_RESULT)
            .toolCallId(toolResult.getToolCallId())
            .toolName(toolResult.getToolName())
            .result(toolResult.getResult())
            .build();
    }

    /**
     * Creates a tool error part for a {@link ModelMessageRole#TOOL} message.
     *
     * @param toolError tool execution error
     * @return a tool error part
     */
    public static ModelMessagePart toolError(ToolError toolError) {
        return ModelMessagePart.builder()
            .type(PartType.TOOL_ERROR)
            .toolCallId(toolError.getToolCallId())
            .toolName(toolError.getToolName())
            .errorText(toolError.getErrorText())
            .build();
    }

    private ModelMessagePart validate() {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("message part type must not be blank");
        }
        switch (type) {
            case PartType.TEXT -> rejectFields("text", signature, approvalId, toolCallId,
                toolName, stepIndex, input, result, errorText, approved, reason, providerOptions,
                media);
            case PartType.IMAGE -> {
                requirePresent(media, "image message part media");
                rejectFields("image", text, signature, approvalId, toolCallId, toolName, stepIndex,
                    input, result, errorText, approved, reason, providerOptions);
            }
            case PartType.FILE -> {
                requirePresent(media, "file message part media");
                rejectFields("file", text, signature, approvalId, toolCallId, toolName, stepIndex,
                    input, result, errorText, approved, reason, providerOptions);
            }
            case PartType.REASONING -> {
                if ((text == null || text.isBlank())
                    && (providerOptions == null || providerOptions.isEmpty())) {
                    throw new IllegalArgumentException(
                        "reasoning message part must include text or provider options");
                }
                rejectFields("reasoning", approvalId, toolCallId, toolName, stepIndex, input,
                    result, errorText, approved, reason, media);
            }
            case PartType.TOOL_CALL -> {
                requireText(toolCallId, "tool-call message part toolCallId");
                requireText(toolName, "tool-call message part toolName");
                rejectFields("tool-call", text, signature, approvalId, stepIndex, result, errorText,
                    approved, reason, providerOptions, media);
            }
            case PartType.TOOL_RESULT -> {
                requireText(toolCallId, "tool-result message part toolCallId");
                requireText(toolName, "tool-result message part toolName");
                rejectFields("tool-result", text, signature, approvalId, stepIndex, input, errorText,
                    approved, reason, providerOptions, media);
            }
            case PartType.TOOL_ERROR -> {
                requireText(toolCallId, "tool-error message part toolCallId");
                requireText(toolName, "tool-error message part toolName");
                requireText(errorText, "tool-error message part errorText");
                rejectFields("tool-error", text, signature, approvalId, stepIndex, input, result,
                    approved, reason, providerOptions, media);
            }
            case PartType.TOOL_APPROVAL_REQUEST -> {
                requireText(approvalId, "tool-approval-request message part approvalId");
                requireText(toolCallId, "tool-approval-request message part toolCallId");
                requireText(toolName, "tool-approval-request message part toolName");
                requireNonNegative(stepIndex, "tool-approval-request message part stepIndex");
                rejectFields("tool-approval-request", text, signature, result, errorText,
                    approved, reason, media);
            }
            case PartType.TOOL_APPROVAL_RESPONSE -> {
                requireText(approvalId, "tool-approval-response message part approvalId");
                requirePresent(approved, "tool-approval-response message part approved");
                rejectFields("tool-approval-response", text, signature, stepIndex, input, result,
                    errorText, media);
            }
            default -> throw new IllegalArgumentException("unsupported message part type: " + type);
        }
        return this;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePresent(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
    }

    private static void requireNonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void rejectFields(String partName, Object... disallowed) {
        for (var value : disallowed) {
            if (value != null) {
                throw new IllegalArgumentException(partName + " message part has invalid fields");
            }
        }
    }

    /**
     * Validating builder for {@link ModelMessagePart}.
     */
    public static class ModelMessagePartBuilder {
        /**
         * Builds and validates a message part.
         *
         * @return validated message part
         * @throws IllegalArgumentException when the selected part type has missing or incompatible
         *                                  fields
         */
        public ModelMessagePart build() {
            return uncheckedBuild().validate();
        }
    }
}
