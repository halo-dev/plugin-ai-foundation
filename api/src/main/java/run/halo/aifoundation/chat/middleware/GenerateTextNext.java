package run.halo.aifoundation.chat.middleware;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;

/**
 * Next non-streaming generation step in a language model middleware chain.
 */
@FunctionalInterface
public interface GenerateTextNext {

    /**
     * Continues generation with the provided request.
     *
     * @param request transformed request
     * @return generated result
     */
    Mono<GenerateTextResult> generate(GenerateTextRequest request);
}
