package run.halo.aifoundation.chat.middleware;

import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.StreamTextResult;

/**
 * Provider-neutral middleware for language model calls.
 *
 * <p>Middleware can transform a request and wrap non-streaming or streaming execution. The default
 * implementation only applies {@link #transformRequest(LanguageModelRequestContext)} and then
 * delegates to the next step.
 */
public interface LanguageModelMiddleware {

    /**
     * Transforms a generation request before model execution.
     *
     * @param context request context
     * @return transformed request
     */
    default Mono<GenerateTextRequest> transformRequest(LanguageModelRequestContext context) {
        return Mono.just(Objects.requireNonNull(context.request(), "request must not be null"));
    }

    /**
     * Wraps non-streaming generation.
     *
     * @param context generate context
     * @param next next generation step
     * @return generated result
     */
    default Mono<GenerateTextResult> wrapGenerate(LanguageModelGenerateContext context,
        GenerateTextNext next) {
        return transformRequest(context).flatMap(next::generate);
    }

    /**
     * Wraps streaming generation.
     *
     * @param context stream context
     * @param next next streaming step
     * @return streaming result
     */
    default StreamTextResult wrapStream(LanguageModelStreamContext context, StreamTextNext next) {
        var transformed = transformRequest(context)
            .map(next::stream)
            .cache();
        return LanguageModelMiddlewares.defer(transformed);
    }
}
