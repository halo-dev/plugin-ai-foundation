package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Context passed to custom UI message validators.
 *
 * @param messages full conversation being validated
 * @param message message currently being validated
 * @param messageIndex zero-based message index
 * @param part part currently being validated, or {@code null} for message-level validation
 * @param partIndex zero-based part index, or {@code -1} for message-level validation
 */
public record UIMessageValidationContext<M>(List<UIMessage<M>> messages,
                                            UIMessage<M> message,
                                            int messageIndex,
                                            UIMessagePart part,
                                            int partIndex) {
    public UIMessageValidationContext {
        messages = List.copyOf(messages);
    }

    /**
     * Returns metadata from the message currently being validated.
     *
     * @return typed message metadata
     */
    public M metadata() {
        return message.metadata();
    }
}
