package run.halo.aifoundation.chat;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata captured before a language model request is sent to a provider.
 *
 * <p>This object is useful for audit logging and lifecycle callbacks. It contains the SDK request
 * id, the resolved provider model id, and caller-supplied metadata. It does not contain prompts or
 * message content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRequestMetadata {
    /**
     * SDK-generated request id for correlating logs and lifecycle events.
     */
    private String id;
    /**
     * Resolved provider model id used for the request.
     */
    private String model;
    /**
     * Caller-supplied metadata copied from {@link GenerateTextRequest#getMetadata()}.
     */
    private Map<String, Object> metadata;
}
