package run.halo.aifoundation.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.part.PartType;

/**
 * A provider-neutral language model message.
 *
 * <p>For simple text conversations, use {@link #system(String)}, {@link #user(String)}, and
 * {@link #assistant(String)}. The {@link #tool(List)} factory is mainly for advanced callers that
 * persist and later replay tool results.
 *
 * <pre>{@code
 * List<ModelMessage> messages = List.of(
 *     ModelMessage.user("Hello"),
 *     ModelMessage.assistant("Hi!"),
 *     ModelMessage.user("Summarize Halo CMS")
 * );
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMessage {
    /**
     * Message role.
     */
    private ModelMessageRole role;
    /**
     * Message content parts.
     */
    private List<ModelMessagePart> content;

    /**
     * Creates a system text message.
     */
    public static ModelMessage system(String text) {
        return of(ModelMessageRole.SYSTEM, text);
    }

    /**
     * Creates a user text message.
     */
    public static ModelMessage user(String text) {
        return of(ModelMessageRole.USER, text);
    }

    /**
     * Creates an assistant text message.
     */
    public static ModelMessage assistant(String text) {
        return of(ModelMessageRole.ASSISTANT, text);
    }

    /**
     * Creates an assistant message with structured content parts, such as text, reasoning, and
     * tool calls.
     */
    public static ModelMessage assistant(List<ModelMessagePart> content) {
        return new ModelMessage(ModelMessageRole.ASSISTANT, content);
    }

    /**
     * Creates a tool message. Content should contain {@link PartType#TOOL_RESULT} or
     * {@link PartType#TOOL_ERROR} parts.
     */
    public static ModelMessage tool(List<ModelMessagePart> content) {
        return new ModelMessage(ModelMessageRole.TOOL, content);
    }

    private static ModelMessage of(ModelMessageRole role, String text) {
        return new ModelMessage(role, List.of(ModelMessagePart.text(text)));
    }
}
