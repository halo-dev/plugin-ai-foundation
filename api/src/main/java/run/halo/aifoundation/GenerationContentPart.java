package run.halo.aifoundation;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A typed content part returned by {@link GenerateTextResult} and {@link GenerationStep}.
 *
 * <p>This mirrors model output after Halo normalization. It may contain normal assistant text,
 * reasoning text, model-requested tool calls, server-side tool results, or server-side tool
 * errors. Consumers that only need final text can read {@link GenerateTextResult#getText()};
 * consumers that need richer traces should inspect {@link #type}.
 */
@Data
@Builder(buildMethodName = "uncheckedBuild")
@NoArgsConstructor
@AllArgsConstructor
public class GenerationContentPart {
                            
    /**
     * Part discriminator. Use values from {@link PartType}.
     */
    private String type;
    /**
     * Generated assistant text for {@link PartType#TEXT}.
     */
    private String text;
    /**
     * Optional provider signature for {@link PartType#REASONING}.
     */
    private String signature;
    /**
     * Tool call id for tool call/result/error parts.
     */
    private String toolCallId;
    /**
     * Tool name for tool call/result/error parts.
     */
    private String toolName;
    /**
     * Parsed tool arguments for {@link PartType#TOOL_CALL}.
     */
    private Map<String, Object> input;
    /**
     * Server-side tool execution result for {@link PartType#TOOL_RESULT}.
     */
    private Object result;
    /**
     * Server-side tool execution error text for {@link PartType#TOOL_ERROR}.
     */
    private String errorText;
    /**
     * Source or file id when the provider returns one.
     */
    private String id;
    /**
     * Source URL or file download URL.
     */
    private String url;
    /**
     * Source or file title/name.
     */
    private String title;
    /**
     * File media type when available.
     */
    private String mediaType;
    /**
     * Generated file bytes or text payload when available. Values should be serializable, for
     * example a base64 string.
     */
    private Object data;
    /**
     * Provider or Halo metadata associated with this content part. Values should be sanitized and
     * serializable.
     */
    private Map<String, Object> metadata;

    /**
     * Creates a generated text content part.
     */
    public static GenerationContentPart text(String text) {
        return GenerationContentPart.builder()
            .type(PartType.TEXT)
            .text(text)
            .build();
    }

    /**
     * Creates a reasoning content part from visible reasoning text.
     */
    public static GenerationContentPart reasoning(String text) {
        return reasoning(ReasoningPart.builder().text(text).build());
    }

    /**
     * Creates a reasoning content part preserving provider metadata needed for continuation.
     */
    public static GenerationContentPart reasoning(ReasoningPart reasoning) {
        return GenerationContentPart.builder()
            .type(PartType.REASONING)
            .text(reasoning.getText())
            .signature(reasoning.getSignature())
            .metadata(reasoning.getProviderMetadata())
            .build();
    }

    /**
     * Creates a content part for a model-requested tool call.
     */
    public static GenerationContentPart toolCall(ToolCall toolCall) {
        return GenerationContentPart.builder()
            .type(PartType.TOOL_CALL)
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .metadata(toolCall.getProviderMetadata())
            .build();
    }

    /**
     * Creates a content part for a successful server-side tool execution.
     */
    public static GenerationContentPart toolResult(ToolResult toolResult) {
        return GenerationContentPart.builder()
            .type(PartType.TOOL_RESULT)
            .toolCallId(toolResult.getToolCallId())
            .toolName(toolResult.getToolName())
            .result(toolResult.getResult())
            .metadata(toolResult.getProviderMetadata())
            .build();
    }

    /**
     * Creates a content part for a failed server-side tool execution.
     */
    public static GenerationContentPart toolError(ToolError toolError) {
        return GenerationContentPart.builder()
            .type(PartType.TOOL_ERROR)
            .toolCallId(toolError.getToolCallId())
            .toolName(toolError.getToolName())
            .errorText(toolError.getErrorText())
            .metadata(toolError.getProviderMetadata())
            .build();
    }

    /**
     * Creates a source reference content part.
     */
    public static GenerationContentPart source(String id, String url, String title,
        Map<String, Object> metadata) {
        return GenerationContentPart.builder()
            .type(PartType.SOURCE)
            .id(id)
            .url(url)
            .title(title)
            .metadata(metadata)
            .build();
    }

    /**
     * Creates a generated file content part.
     */
    public static GenerationContentPart file(String id, String url, String title, String mediaType,
        Object data, Map<String, Object> metadata) {
        return GenerationContentPart.builder()
            .type(PartType.FILE)
            .id(id)
            .url(url)
            .title(title)
            .mediaType(mediaType)
            .data(data)
            .metadata(metadata)
            .build();
    }

    private GenerationContentPart validate() {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("generation content part type must not be blank");
        }
        switch (type) {
            case PartType.TEXT -> rejectFields("text", signature, toolCallId, toolName, input,
                result, errorText, id, url, title, mediaType, data);
            case PartType.REASONING -> rejectFields("reasoning",
                toolCallId, toolName, input, result, errorText, id, url, title, mediaType, data);
            case PartType.TOOL_CALL -> {
                requireText(toolCallId, "tool-call content part toolCallId");
                requireText(toolName, "tool-call content part toolName");
                rejectFields("tool-call", text, signature, result, errorText, id,
                    url, title, mediaType, data);
            }
            case PartType.TOOL_RESULT -> {
                requireText(toolCallId, "tool-result content part toolCallId");
                requireText(toolName, "tool-result content part toolName");
                rejectFields("tool-result", text, signature, input, errorText, id,
                    url, title, mediaType, data);
            }
            case PartType.TOOL_ERROR -> {
                requireText(toolCallId, "tool-error content part toolCallId");
                requireText(toolName, "tool-error content part toolName");
                requireText(errorText, "tool-error content part errorText");
                rejectFields("tool-error", text, signature, input, result, id,
                    url, title, mediaType, data);
            }
            case PartType.SOURCE -> rejectFields("source", text,
                signature, toolCallId, toolName, input, result, errorText, mediaType, data);
            case PartType.FILE -> rejectFields("file",
                text, signature, toolCallId, toolName, input, result, errorText);
            default -> throw new IllegalArgumentException("unsupported generation content part type: "
                + type);
        }
        return this;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void rejectFields(String partName, Object... disallowed) {
        for (var value : disallowed) {
            if (value != null) {
                throw new IllegalArgumentException(partName + " generation content part has invalid fields");
            }
        }
    }

    public static class GenerationContentPartBuilder {
        public GenerationContentPart build() {
            return uncheckedBuild().validate();
        }
    }
}
