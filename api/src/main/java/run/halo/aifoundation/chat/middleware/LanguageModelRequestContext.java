package run.halo.aifoundation.chat.middleware;

import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;

/**
 * Common context for language model middleware request transformation.
 */
public interface LanguageModelRequestContext {

    /**
     * Model being wrapped.
     *
     * @return language model
     */
    LanguageModel model();

    /**
     * Current generation request.
     *
     * @return generation request
     */
    GenerateTextRequest request();
}
