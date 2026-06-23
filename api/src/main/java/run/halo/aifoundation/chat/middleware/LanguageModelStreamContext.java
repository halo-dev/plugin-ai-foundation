package run.halo.aifoundation.chat.middleware;

import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.LanguageModel;

/**
 * Context passed to streaming language model middleware.
 *
 * @param model model being wrapped
 * @param request current generation request
 */
public record LanguageModelStreamContext(LanguageModel model, GenerateTextRequest request)
    implements LanguageModelRequestContext {
}
