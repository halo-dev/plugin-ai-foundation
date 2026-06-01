package run.halo.aifoundation.lifecycle;

import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.control.CancellationSource;

/**
 * Request-scoped lifecycle callbacks for text generation.
 *
 * <p>Callbacks are observers: an exception thrown by a callback is captured as a
 * {@link GenerationWarning} and does not fail an otherwise successful generation. Use
 * {@link CancellationSource} when callback code needs to stop the generation explicitly.
 *
 * <pre>{@code
 * var lifecycle = new GenerationLifecycle() {
 *     @Override
 *     public Mono<Void> onStepFinish(GenerationStepFinishEvent event) {
 *         log.info("step {} finished with {}", event.getStepIndex(),
 *             event.getStep().getFinishReason());
 *         return Mono.empty();
 *     }
 * };
 *
 * var request = GenerateTextRequest.builder()
 *     .prompt("Summarize Halo CMS")
 *     .lifecycle(lifecycle)
 *     .build();
 * }</pre>
 */
public interface GenerationLifecycle {

    default Mono<Void> onStart(GenerationStartEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onStepStart(GenerationStepStartEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onToolCallStart(GenerationToolCallStartEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onToolCallFinish(GenerationToolCallFinishEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onStepFinish(GenerationStepFinishEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onFinish(GenerationFinishEvent event) {
        return Mono.empty();
    }

    default Mono<Void> onError(GenerationErrorEvent event) {
        return Mono.empty();
    }
}
