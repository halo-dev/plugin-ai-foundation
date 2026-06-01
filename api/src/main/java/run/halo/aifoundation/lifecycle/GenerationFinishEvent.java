package run.halo.aifoundation.lifecycle;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextResult;

/**
 * Lifecycle event emitted after a text generation request finishes successfully.
 *
 * <p>The {@link #getResult()} value is the same normalized result returned to the caller, including
 * text, steps, usage, warnings, tool calls, and structured output when configured.
 */
@Value
@Builder
public class GenerationFinishEvent {
    /**
     * Final normalized generation result.
     */
    GenerateTextResult result;
    /**
     * Caller-supplied metadata copied from the request.
     */
    Map<String, Object> metadata;
    /**
     * Caller-supplied context copied from the request.
     */
    Map<String, Object> context;
}
