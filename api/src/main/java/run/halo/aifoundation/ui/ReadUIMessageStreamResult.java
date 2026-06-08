package run.halo.aifoundation.ui;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Result returned after a UI message stream is connected to the reader.
 *
 * @param <M> message metadata type
 */
public final class ReadUIMessageStreamResult<M> {

    private final Flux<UIMessage<M>> messages;
    private final Mono<UIMessage<M>> responseMessage;
    private final Mono<UIMessageStreamTerminal> finish;

    /**
     * Creates a reader result.
     *
     * @param messages response message snapshots emitted as parts change
     * @param responseMessage final aggregated assistant response
     * @param finish terminal finish, error, or abort state
     */
    public ReadUIMessageStreamResult(Flux<UIMessage<M>> messages,
        Mono<UIMessage<M>> responseMessage, Mono<UIMessageStreamTerminal> finish) {
        this.messages = messages;
        this.responseMessage = responseMessage;
        this.finish = finish;
    }

    /**
     * Returns response message snapshots.
     *
     * @return snapshots emitted whenever persisted message content changes
     */
    public Flux<UIMessage<M>> messages() {
        return messages;
    }

    /**
     * Returns the final aggregated assistant response message.
     *
     * @return final response message
     */
    public Mono<UIMessage<M>> responseMessage() {
        return responseMessage;
    }

    /**
     * Returns terminal stream state.
     *
     * @return terminal finish, error, or abort state
     */
    public Mono<UIMessageStreamTerminal> finish() {
        return finish;
    }
}
