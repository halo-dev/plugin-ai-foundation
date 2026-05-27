package run.halo.aifoundation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Result returned by {@link LanguageModel#streamText(GenerateTextRequest)}.
 *
 * <p>The views exposed by this class are different projections of one streaming generation. A
 * provider implementation must not start a second model request when callers consume more than one
 * view.
 *
 * <pre>{@code
 * StreamTextResult result = model.streamText(request);
 *
 * result.textStream()
 *     .doOnNext(System.out::print)
 *     .then(result.result())
 *     .subscribe(finalResult -> log.info("finish={}", finalResult.getFinishReason()));
 * }</pre>
 */
public class StreamTextResult {

    private final Flux<TextStreamPart> fullStream;

    private final Flux<String> textStream;

    private final Flux<Object> partialOutputStream;

    private final Flux<Object> elementStream;

    private final Mono<Object> output;

    private final Mono<GenerateTextResult> result;

    public StreamTextResult(Flux<TextStreamPart> fullStream, Flux<String> textStream,
        Flux<Object> partialOutputStream, Flux<Object> elementStream, Mono<Object> output,
        Mono<GenerateTextResult> result) {
        this.fullStream = fullStream;
        this.textStream = textStream;
        this.partialOutputStream = partialOutputStream;
        this.elementStream = elementStream;
        this.output = output;
        this.result = result;
    }

    /**
     * Full Halo stream protocol with lifecycle, text, reasoning, tool, raw, finish, and error
     * parts.
     */
    public Flux<TextStreamPart> fullStream() {
        return fullStream;
    }

    /**
     * Generated answer text deltas only.
     */
    public Flux<String> textStream() {
        return textStream;
    }

    /**
     * Best-effort partial structured outputs. Partial values are not final schema validation
     * success.
     */
    public Flux<Object> partialOutputStream() {
        return partialOutputStream;
    }

    /**
     * Completed and validated array elements for {@link OutputSpec#array(java.util.Map)} and
     * {@link OutputSpec#array(Class)} requests.
     */
    public Flux<Object> elementStream() {
        return elementStream;
    }

    /**
     * Complete parsed structured output after final validation succeeds.
     */
    public Mono<Object> output() {
        return output;
    }

    /**
     * Final accumulated generation result.
     */
    public Mono<GenerateTextResult> result() {
        return result;
    }
}
