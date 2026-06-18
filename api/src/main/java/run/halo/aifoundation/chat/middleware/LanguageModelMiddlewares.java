package run.halo.aifoundation.chat.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.part.TextStreamPart;

/**
 * Helpers for composing language model middleware.
 */
public final class LanguageModelMiddlewares {

    private LanguageModelMiddlewares() {
    }

    /**
     * Wraps a model with model-level middleware.
     *
     * <p>Middleware is applied in list order. Request-level middleware attached to
     * {@link GenerateTextRequest} runs inside model-level middleware and also preserves list order.
     *
     * @param model base model
     * @param middleware model-level middleware
     * @return wrapped language model
     */
    public static LanguageModel wrap(LanguageModel model, List<LanguageModelMiddleware> middleware) {
        Objects.requireNonNull(model, "model must not be null");
        var chain = List.copyOf(Objects.requireNonNull(middleware, "middleware must not be null"));
        if (chain.isEmpty()) {
            return model;
        }
        return new WrappedLanguageModel(model, chain);
    }

    /**
     * Wraps a model with model-level middleware.
     *
     * @param model base model
     * @param middleware model-level middleware
     * @return wrapped language model
     */
    public static LanguageModel wrap(LanguageModel model, LanguageModelMiddleware... middleware) {
        return wrap(model, List.of(middleware));
    }

    /**
     * Applies request-level middleware attached to a generation request.
     *
     * <p>This is primarily used by model implementations that want to honor request middleware
     * without adding model-level middleware.
     *
     * @param model terminal model
     * @param request request that may contain request-level middleware
     * @return generated result
     */
    public static Mono<GenerateTextResult> applyRequestMiddleware(LanguageModel model,
        GenerateTextRequest request) {
        return generate(model, request, requestMiddleware(request));
    }

    /**
     * Applies request-level middleware attached to a streaming generation request.
     *
     * @param model terminal model
     * @param request request that may contain request-level middleware
     * @return stream result
     */
    public static StreamTextResult applyRequestStreamMiddleware(LanguageModel model,
        GenerateTextRequest request) {
        return stream(model, request, requestMiddleware(request));
    }

    /**
     * Creates a deferred stream result from an asynchronously prepared stream result.
     *
     * @param result async stream result
     * @return deferred stream result
     */
    public static StreamTextResult defer(Mono<StreamTextResult> result) {
        var cached = Objects.requireNonNull(result, "result must not be null").cache();
        Flux<TextStreamPart> fullStream = cached.flatMapMany(StreamTextResult::fullStream).cache();
        var textStream = fullStream
            .filter(part -> "text-delta".equals(part.getType()))
            .map(TextStreamPart::getDelta)
            .filter(delta -> delta != null && !delta.isEmpty());
        return new StreamTextResult(
            fullStream,
            textStream,
            cached.flatMapMany(StreamTextResult::partialOutputStream),
            cached.flatMapMany(StreamTextResult::elementStream),
            cached.flatMap(StreamTextResult::output),
            cached.flatMap(StreamTextResult::result)
        );
    }

    private static Mono<GenerateTextResult> generate(LanguageModel model, GenerateTextRequest request,
        List<LanguageModelMiddleware> middleware) {
        Objects.requireNonNull(model, "model must not be null");
        var effectiveRequest = request != null ? request : GenerateTextRequest.builder().build();
        return generateAt(model, effectiveRequest, middleware, 0);
    }

    private static Mono<GenerateTextResult> generateAt(LanguageModel model, GenerateTextRequest request,
        List<LanguageModelMiddleware> middleware, int index) {
        if (index >= middleware.size()) {
            return model.generateText(withoutMiddleware(request));
        }
        var current = middleware.get(index);
        return current.wrapGenerate(new LanguageModelGenerateContext(model, request),
            nextRequest -> generateAt(model, nextRequest, middleware, index + 1));
    }

    private static StreamTextResult stream(LanguageModel model, GenerateTextRequest request,
        List<LanguageModelMiddleware> middleware) {
        Objects.requireNonNull(model, "model must not be null");
        var effectiveRequest = request != null ? request : GenerateTextRequest.builder().build();
        return streamAt(model, effectiveRequest, middleware, 0);
    }

    private static StreamTextResult streamAt(LanguageModel model, GenerateTextRequest request,
        List<LanguageModelMiddleware> middleware, int index) {
        if (index >= middleware.size()) {
            return model.streamText(withoutMiddleware(request));
        }
        var current = middleware.get(index);
        return current.wrapStream(new LanguageModelStreamContext(model, request),
            nextRequest -> streamAt(model, nextRequest, middleware, index + 1));
    }

    private static List<LanguageModelMiddleware> requestMiddleware(GenerateTextRequest request) {
        if (request == null || request.getMiddleware() == null || request.getMiddleware().isEmpty()) {
            return List.of();
        }
        return List.copyOf(request.getMiddleware());
    }

    private static GenerateTextRequest withoutMiddleware(GenerateTextRequest request) {
        if (request == null || request.getMiddleware() == null || request.getMiddleware().isEmpty()) {
            return request;
        }
        return GenerateTextRequest.builder()
            .system(request.getSystem())
            .prompt(request.getPrompt())
            .messages(request.getMessages())
            .maxOutputTokens(request.getMaxOutputTokens())
            .temperature(request.getTemperature())
            .topP(request.getTopP())
            .topK(request.getTopK())
            .presencePenalty(request.getPresencePenalty())
            .frequencyPenalty(request.getFrequencyPenalty())
            .stopSequences(request.getStopSequences())
            .seed(request.getSeed())
            .maxRetries(request.getMaxRetries())
            .providerOptions(request.getProviderOptions())
            .reasoning(request.getReasoning())
            .headers(request.getHeaders())
            .metadata(request.getMetadata())
            .context(request.getContext())
            .output(request.getOutput())
            .tools(request.getTools())
            .toolChoice(request.getToolChoice())
            .stopWhen(request.getStopWhen())
            .prepareStep(request.getPrepareStep())
            .lifecycle(request.getLifecycle())
            .toolCallRepair(request.getToolCallRepair())
            .cancellationToken(request.getCancellationToken())
            .timeouts(request.getTimeouts())
            .build();
    }

    private static final class WrappedLanguageModel implements LanguageModel {
        private final LanguageModel delegate;
        private final List<LanguageModelMiddleware> middleware;

        private WrappedLanguageModel(LanguageModel delegate, List<LanguageModelMiddleware> middleware) {
            this.delegate = delegate;
            this.middleware = middleware;
        }

        @Override
        public Mono<GenerateTextResult> generateText(String prompt) {
            return generateText(GenerateTextRequest.builder().prompt(prompt).build());
        }

        @Override
        public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
            var chain = new ArrayList<>(middleware);
            chain.addAll(requestMiddleware(request));
            return generate(delegate, request, chain);
        }

        @Override
        public StreamTextResult streamText(GenerateTextRequest request) {
            var chain = new ArrayList<>(middleware);
            chain.addAll(requestMiddleware(request));
            return stream(delegate, request, chain);
        }

        @Override
        public LanguageModelCapabilities capabilities() {
            return delegate.capabilities();
        }
    }
}
