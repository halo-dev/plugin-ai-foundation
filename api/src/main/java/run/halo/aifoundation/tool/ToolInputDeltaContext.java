package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.message.ModelMessage;

/**
 * Immutable provider-neutral snapshot for one streamed tool input delta callback.
 */
@Value
public class ToolInputDeltaContext {
    String toolCallId;
    String toolName;
    String inputTextDelta;
    Integer stepIndex;
    List<ModelMessage> messages;
    Map<String, Object> requestContext;
    Map<String, Object> providerMetadata;
    CancellationToken cancellationToken;

    @Builder
    public ToolInputDeltaContext(String toolCallId, String toolName, String inputTextDelta,
        Integer stepIndex, List<ModelMessage> messages, Map<String, Object> requestContext,
        Map<String, Object> providerMetadata, CancellationToken cancellationToken) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.inputTextDelta = inputTextDelta;
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
