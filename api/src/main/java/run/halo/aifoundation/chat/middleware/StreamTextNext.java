package run.halo.aifoundation.chat.middleware;

import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StreamTextResult;

/**
 * Next streaming generation step in a language model middleware chain.
 */
@FunctionalInterface
public interface StreamTextNext {

    /**
     * Continues streaming with the provided request.
     *
     * @param request transformed request
     * @return stream result
     */
    StreamTextResult stream(GenerateTextRequest request);
}
