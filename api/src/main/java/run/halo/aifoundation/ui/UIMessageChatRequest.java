package run.halo.aifoundation.ui;

import java.util.List;
import java.util.Objects;

/**
 * Framework-neutral request model for chat transport submissions.
 *
 * @param id caller-defined chat or request id
 * @param messages persisted conversation messages sent by the caller
 * @param trigger chat action to perform
 * @param messageId assistant message id used by regeneration requests
 */
public record UIMessageChatRequest<M>(String id, List<UIMessage<M>> messages,
                                      UIMessageChatTrigger trigger, String messageId) {

    public UIMessageChatRequest {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        Objects.requireNonNull(trigger, "trigger must not be null");
        if (messageId != null && messageId.isBlank()) {
            messageId = null;
        }
    }
}
