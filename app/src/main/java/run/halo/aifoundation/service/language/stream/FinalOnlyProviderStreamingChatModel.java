package run.halo.aifoundation.service.language.stream;

import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Adapts a normal Spring AI chat model without fabricating incremental tool input.
 */
public final class FinalOnlyProviderStreamingChatModel implements ProviderStreamingChatModel {

    private final ChatModel delegate;

    public FinalOnlyProviderStreamingChatModel(ChatModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public Flux<ProviderStreamPart> streamParts(Prompt prompt) {
        return delegate.stream(prompt)
            .map(ProviderStreamPart.ChatResponsePart::new);
    }
}
