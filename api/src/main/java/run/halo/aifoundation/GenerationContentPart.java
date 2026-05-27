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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationContentPart {
    public static final String TYPE_TEXT = PartType.TEXT;
    public static final String TYPE_TOOL_CALL = PartType.TOOL_CALL;
    public static final String TYPE_TOOL_RESULT = PartType.TOOL_RESULT;
    public static final String TYPE_TOOL_ERROR = PartType.TOOL_ERROR;
    public static final String TYPE_REASONING = PartType.REASONING;
    public static final String TYPE_SOURCE = PartType.SOURCE;
    public static final String TYPE_FILE = PartType.FILE;

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
            .type(TYPE_TEXT)
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
            .type(TYPE_REASONING)
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
            .type(TYPE_TOOL_CALL)
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
            .type(TYPE_TOOL_RESULT)
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
            .type(TYPE_TOOL_ERROR)
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
            .type(TYPE_SOURCE)
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
            .type(TYPE_FILE)
            .id(id)
            .url(url)
            .title(title)
            .mediaType(mediaType)
            .data(data)
            .metadata(metadata)
            .build();
    }
}
