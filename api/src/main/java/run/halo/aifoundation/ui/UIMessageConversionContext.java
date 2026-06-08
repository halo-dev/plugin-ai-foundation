package run.halo.aifoundation.ui;

import java.util.List;

/**
 * Context passed to custom UI message conversion hooks.
 *
 * @param messages full validated conversation
 * @param message message currently being converted
 * @param messageIndex zero-based message index
 * @param part part currently being converted
 * @param partIndex zero-based part index
 */
public record UIMessageConversionContext<M>(List<UIMessage<M>> messages,
                                            UIMessage<M> message,
                                            int messageIndex,
                                            UIMessagePart part,
                                            int partIndex) {
    public UIMessageConversionContext {
        messages = List.copyOf(messages);
    }

    /**
     * Returns metadata from the message currently being converted.
     *
     * @return typed message metadata
     */
    public M metadata() {
        return message.metadata();
    }
}
