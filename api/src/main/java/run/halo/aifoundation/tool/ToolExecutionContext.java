package run.halo.aifoundation.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Provider-neutral context passed to a server-side tool executor.
 *
 * <p>The context includes the parsed input and enough call metadata to correlate tool execution with stream events and
 * persisted conversation history.
 *
 * <p>Read model-generated arguments from {@link #input}. Request context is caller-supplied application data and is not
 * inserted into prompts; only values established by trusted server code should be used for authorization. Check
 * {@link #cancellationToken} during lengthy work.
 *
 * <pre>{@code
 * String articleName = (String) context.getInput().get("name");
 * return articleService.read(articleName).cast(Object.class);
 * }</pre>
 */
@Value
public class ToolExecutionContext {
    /** Provider or Halo generated tool call id. */
    private String toolCallId;
    /** Name of the tool being executed. */
    private String toolName;
    /** Parsed JSON arguments produced by the model. */
    private Map<String, Object> input;
    /** Zero-based model invocation step that produced the tool call. */
    private Integer stepIndex;
    /** Provider-neutral messages sent to the model for the step that produced this tool call. */
    private List<ModelMessage> messages;
    /** Provider metadata from the tool call and surrounding step. */
    private Map<String, Object> providerMetadata;
    /** Caller request context. This data is not added to model prompts. */
    private Map<String, Object> requestContext;
    /** Request-scoped cooperative cancellation signal, when the caller supplied one. */
    private CancellationToken cancellationToken;

    /**
     * Creates an execution snapshot, replacing absent maps and messages with empty collections.
     *
     * @param toolCallId original model call identity
     * @param toolName model-facing tool name
     * @param input validated JSON arguments
     * @param stepIndex zero-based model invocation step
     * @param messages messages used for this step
     * @param providerMetadata provider or Halo metadata
     * @param requestContext caller application context, excluded from prompts
     * @param cancellationToken optional cooperative cancellation signal
     */
    @Builder
    public ToolExecutionContext(
            String toolCallId,
            String toolName,
            Map<String, Object> input,
            Integer stepIndex,
            List<ModelMessage> messages,
            Map<String, Object> providerMetadata,
            Map<String, Object> requestContext,
            CancellationToken cancellationToken) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.input = immutableMap(input);
        this.stepIndex = stepIndex;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.providerMetadata = immutableMap(providerMetadata);
        this.requestContext = immutableMap(requestContext);
        this.cancellationToken = cancellationToken;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return source == null || source.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
