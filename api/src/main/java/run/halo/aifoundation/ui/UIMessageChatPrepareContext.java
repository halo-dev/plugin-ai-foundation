package run.halo.aifoundation.ui;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerateTextRequest;

/**
 * Context passed to an async UI message chat prepare hook.
 */
@Value
@Builder
public class UIMessageChatPrepareContext<M> {

    UIMessageChatRequest<M> chatRequest;

    List<UIMessage<M>> messages;

    UIMessageConversionResult conversion;

    GenerateTextRequest.GenerateTextRequestBuilder requestBuilder;
}
