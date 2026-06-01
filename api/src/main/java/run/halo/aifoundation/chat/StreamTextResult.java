package run.halo.aifoundation.chat;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.ReasoningPart;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolResult;

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

    /**
     * Full provider-neutral stream event view for the generation.
     */
    private final Flux<TextStreamPart> fullStream;

    /**
     * Convenience stream containing only generated answer text deltas.
     */
    private final Flux<String> textStream;

    /**
     * Progressive structured-output snapshots when the output text is parseable.
     */
    private final Flux<Object> partialOutputStream;

    /**
     * Completed structured array elements when array output is configured.
     */
    private final Flux<Object> elementStream;

    /**
     * Final parsed structured output, when structured output is configured.
     */
    private final Mono<Object> output;

    /**
     * Final normalized generation result.
     */
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

    public Mono<String> text() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getText()));
    }

    public Mono<String> reasoningText() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getReasoningText()));
    }

    public Mono<List<GenerationContentPart>> content() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getContent()));
    }

    public Mono<List<ReasoningPart>> reasoning() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getReasoning()));
    }

    public Mono<FinishReason> finishReason() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getFinishReason()));
    }

    public Mono<String> rawFinishReason() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getRawFinishReason()));
    }

    public Mono<LanguageModelUsage> usage() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getUsage()));
    }

    public Mono<LanguageModelUsage> totalUsage() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getTotalUsage()));
    }

    public Mono<List<GenerationWarning>> warnings() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getWarnings()));
    }

    public Mono<GenerationRequestMetadata> request() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getRequest()));
    }

    public Mono<GenerationResponseMetadata> response() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getResponse()));
    }

    public Mono<List<GenerationStep>> steps() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getSteps()));
    }

    public Mono<List<ToolCall>> toolCalls() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getToolCalls()));
    }

    public Mono<List<ToolResult>> toolResults() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getToolResults()));
    }

    public Mono<List<ToolError>> toolErrors() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getToolErrors()));
    }

    public Mono<Map<String, Object>> providerMetadata() {
        return result.flatMap(value -> Mono.justOrEmpty(value.getProviderMetadata()));
    }
}
