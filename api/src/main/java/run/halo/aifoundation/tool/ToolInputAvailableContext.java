package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Immutable provider-neutral snapshot for normalized tool input availability.
 *
 * <p>The runtime supplies this snapshot after parsing and validation, before approval or execution. Maps and message
 * lists cannot be modified through this context. Use the tool call identity to correlate observations across callbacks,
 * and honor the optional cancellation signal during asynchronous work.
 */
@Value
public class ToolInputAvailableContext {
    /** Original model tool call identity, used to correlate later input and result events. */
    String toolCallId;
    /** Model-facing name of the tool whose input is being processed. */
    String toolName;
    /** Validated parsed JSON arguments available to approval and execution. */
    Map<String, Object> input;
    /** Zero-based model invocation step that produced this callback. */
    Integer stepIndex;
    /** Immutable list of provider-neutral messages used for this generation step. */
    List<ModelMessage> messages;
    /** Caller application context, excluded from model prompts. */
    Map<String, Object> requestContext;
    /** Provider or Halo metadata associated with this call and step. */
    Map<String, Object> providerMetadata;
    /** Optional cooperative cancellation signal for request-scoped work. */
    CancellationToken cancellationToken;

    /**
     * Creates a callback snapshot with immutable collection copies.
     *
     * @param toolCallId original model call identity
     * @param toolName model-facing tool name
     * @param input validated parsed JSON arguments
     * @param stepIndex zero-based model invocation step
     * @param messages messages used for this generation step
     * @param requestContext caller application context
     * @param providerMetadata provider or Halo metadata
     * @param cancellationToken optional cooperative cancellation signal
     */
    @Builder
    public ToolInputAvailableContext(
            String toolCallId,
            String toolName,
            Map<String, Object> input,
            Integer stepIndex,
            List<ModelMessage> messages,
            Map<String, Object> requestContext,
            Map<String, Object> providerMetadata,
            CancellationToken cancellationToken) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.input = immutableMap(input);
        this.stepIndex = stepIndex;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.requestContext = immutableMap(requestContext);
        this.providerMetadata = immutableMap(providerMetadata);
        this.cancellationToken = cancellationToken;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return source == null || source.isEmpty() ? Map.of() : Map.copyOf(source);
    }
}
