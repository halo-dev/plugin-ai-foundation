package run.halo.aifoundation.service.language.stream;

import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * App-internal provider stream that preserves ordered events discarded by aggregated Spring AI
 * chat responses.
 */
public interface ProviderStreamingChatModel {

    Flux<ProviderStreamPart> streamParts(Prompt prompt);
}
